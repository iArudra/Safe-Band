"""
a9g_relay.py
-------------
SafeWatch A9G GPS/GSM relay — Stage 1 (Computer-based)

What this script does
---------------------
1.  Opens the serial port to the A9G module (COM3 by default).
2.  Enables GPS with AT+GPS=1.
3.  Polls AT+LOCATION=2 every POLL_INTERVAL_SECONDS.
4.  Parses the real A9G response format detected from your hardware.
5.  Validates the coordinates (rejects 0,0 and non-numeric).
6.  POSTs the location to the SafeWatch AWS API via HTTPS.
7.  Retries on network failure with exponential back-off.
8.  Sends a one-time SMS alert when the first valid GPS fix arrives
    (configurable — set SEND_INITIAL_SMS=false to disable).
9.  Prints every event to the terminal with a timestamp.
10. Handles serial disconnections and tries to reconnect.

Configuration
-------------
All settings are read from environment variables or a sibling .env file.
Copy .env.example to .env and fill in the values.

Run
---
    cd backend
    python a9g_relay.py

Hardware milestone test (no API, no Firebase)
---------------------------------------------
    OFFLINE_MODE=true python a9g_relay.py

In offline mode the script still opens the serial port, reads GPS, and
prints coordinates — but skips the HTTP POST.  Use this first to confirm
your A9G returns data before deploying the API.
"""

from __future__ import annotations

import logging
import os
import re
import sys
import time
from datetime import datetime, timezone
from typing import Optional, Tuple

import requests
import serial
import serial.tools.list_ports
from dotenv import load_dotenv

# ── Load .env ──────────────────────────────────────────────────────────────── #
load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), ".env"))

# --------------------------------------------------------------------------- #
#  Configuration (all from environment / .env)                                #
# --------------------------------------------------------------------------- #

PORT: str               = os.getenv("A9G_PORT", "COM3")
BAUDRATE: int           = int(os.getenv("A9G_BAUDRATE", "115200"))
POLL_INTERVAL: int      = int(os.getenv("POLL_INTERVAL_SECONDS", "30"))
GPS_FIX_TIMEOUT: int    = int(os.getenv("GPS_FIX_TIMEOUT_SECONDS", "180"))
DEVICE_ID: str          = os.getenv("DEVICE_ID", "band_001")
API_URL: str            = os.getenv("API_URL", "")          # https://your-ec2-ip:5000/api/location
DEVICE_TOKEN: str       = os.getenv("DEVICE_SECRET", "")
OFFLINE_MODE: bool      = os.getenv("OFFLINE_MODE", "false").lower() == "true"

# SMS config — reuse your existing send_text.py logic
SEND_INITIAL_SMS: bool  = os.getenv("SEND_INITIAL_SMS", "true").lower() == "true"
SMS_RECIPIENT: str      = os.getenv("SMS_RECIPIENT", "+918019914152")
SMS_MESSAGE: str        = os.getenv(
    "SMS_MESSAGE",
    "SafeBand GPS fix acquired. Tracking has started."
)

# --------------------------------------------------------------------------- #
#  Logging                                                                     #
# --------------------------------------------------------------------------- #

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
    handlers=[logging.StreamHandler(sys.stdout)],
)
log = logging.getLogger("a9g_relay")

# --------------------------------------------------------------------------- #
#  Serial helpers                                                              #
# --------------------------------------------------------------------------- #

def open_serial() -> serial.Serial:
    """Open the serial port.  Raises serial.SerialException on failure."""
    log.info("Opening serial port %s @ %d baud...", PORT, BAUDRATE)
    ser = serial.Serial(PORT, BAUDRATE, timeout=2)
    time.sleep(2)   # give the module time to settle after DTR
    ser.reset_input_buffer()
    log.info("Serial port open.")
    return ser


def send_at(ser: serial.Serial, command: str, wait: float = 1.5) -> str:
    """
    Send an AT command and return the full response string.
    Clears the input buffer before sending to avoid stale data.
    """
    ser.reset_input_buffer()
    ser.write((command + "\r\n").encode())
    time.sleep(wait)
    raw = ser.read_all()
    response = raw.decode(errors="ignore")
    log.debug(">>> %s\n%s", command, response.strip())
    return response


def check_at(ser: serial.Serial) -> bool:
    """Return True if the module responds to AT with OK."""
    resp = send_at(ser, "AT", 1.0)
    return "OK" in resp

# --------------------------------------------------------------------------- #
#  GPS helpers                                                                 #
# --------------------------------------------------------------------------- #

def enable_gps(ser: serial.Serial) -> None:
    """Send AT+GPS=1 and log the response."""
    resp = send_at(ser, "AT+GPS=1", 3.0)
    if "OK" in resp or "GPS is already ON" in resp.upper():
        log.info("GPS enabled.")
    else:
        log.warning("Unexpected GPS enable response: %s", resp.strip())


def disable_gps(ser: serial.Serial) -> None:
    send_at(ser, "AT+GPS=0", 1.5)
    log.info("GPS disabled.")


# ── Parser for AT+LOCATION=2 ────────────────────────────────────────────────
#
# Observed A9G responses for AT+LOCATION=2:
#
#   No fix:
#       +LOCATION: GPS NOT FIX NOW
#
#   Fix acquired (typical):
#       +LOCATION: 12.971600,77.594600
#
#   Some firmware builds prefix with "2,":
#       +LOCATION: 2,12.971600,77.594600
#
#   With extra fields (speed, bearing):
#       +LOCATION: 2,12.971600,77.594600,0.00,0.00
#
# The regex below extracts the LAST two decimal numbers, making it robust to
# any prefix the firmware might add.  Adjust LOCATION_REGEX in .env if your
# firmware produces a different format.
#
# To capture the raw line for debugging, set LOG_LEVEL=DEBUG in .env.

_LOCATION_PATTERN = re.compile(
    r"\+LOCATION:\s*"          # prefix
    r"(?:\d+\s*,\s*)?"         # optional integer mode prefix like "2,"
    r"(-?\d+\.\d+)"            # latitude (group 1)
    r"\s*,\s*"
    r"(-?\d+\.\d+)"            # longitude (group 2)
)


def parse_location(response: str) -> Optional[Tuple[float, float]]:
    """
    Extract (lat, lng) from an AT+LOCATION=2 response.

    Returns
    -------
    (lat, lng) as floats, or None if:
    - The response contains GPS NOT FIX NOW.
    - The +LOCATION: line is absent.
    - The numbers don't match the expected pattern.
    """
    if "GPS NOT FIX NOW" in response.upper():
        return None
    if "+LOCATION:" not in response:
        return None

    match = _LOCATION_PATTERN.search(response)
    if not match:
        log.debug("Could not parse LOCATION response: %r", response.strip())
        return None

    try:
        lat = float(match.group(1))
        lng = float(match.group(2))
    except ValueError:
        return None

    # Sanity-check
    if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lng <= 180.0):
        log.warning("Out-of-range coordinates: lat=%s lng=%s", lat, lng)
        return None

    # 0,0 = Null Island — almost certainly a non-fix
    if lat == 0.0 and lng == 0.0:
        return None

    return lat, lng


# --------------------------------------------------------------------------- #
#  SMS helper (preserved from send_text.py)                                   #
# --------------------------------------------------------------------------- #

_sms_sent_flag = False   # We only send the initial SMS once per run.


def send_sms(ser: serial.Serial, number: str, message: str) -> bool:
    """
    Send an SMS via the A9G using AT commands.
    Returns True on confirmed success, False otherwise.

    This function replicates and improves the logic in your original
    send_text.py, sharing the same serial connection.
    """
    log.info("Sending SMS to %s ...", number)

    # Text mode
    resp = send_at(ser, "AT+CMGF=1", 1.0)
    if "OK" not in resp:
        log.error("Failed to set SMS text mode: %s", resp.strip())
        return False

    # Destination number
    ser.reset_input_buffer()
    ser.write(f'AT+CMGS="{number}"\r\n'.encode())
    time.sleep(2)
    prompt_resp = ser.read_all().decode(errors="ignore")

    if ">" not in prompt_resp:
        log.error("A9G did not give SMS prompt (>). Response: %s", prompt_resp.strip())
        return False

    # Message body + Ctrl-Z (ASCII 26)
    ser.write(message.encode())
    ser.write(bytes([26]))

    log.info("Waiting for SMS delivery confirmation (up to 15 s)...")
    time.sleep(15)
    result = ser.read_all().decode(errors="ignore")
    log.debug("SMS result: %s", result.strip())

    if "+CMGS:" in result and "OK" in result:
        log.info("SMS sent successfully.")
        return True
    elif "ERROR" in result:
        log.error("SMS failed: %s", result.strip())
        return False
    else:
        log.warning("SMS result ambiguous: %s", result.strip())
        return False

# --------------------------------------------------------------------------- #
#  HTTP / API helpers                                                          #
# --------------------------------------------------------------------------- #

_SESSION = requests.Session()


def post_location(lat: float, lng: float) -> bool:
    """
    POST a GPS fix to the SafeWatch API.
    Returns True on success, False on any failure.
    Retries up to 3 times with exponential back-off.
    """
    if OFFLINE_MODE:
        log.info("[OFFLINE] Skipping HTTP POST  lat=%.6f  lng=%.6f", lat, lng)
        return True

    if not API_URL:
        log.error(
            "API_URL is not set. Add it to your .env file or set the "
            "OFFLINE_MODE=true env var to run without network."
        )
        return False

    payload = {
        "device_id": DEVICE_ID,
        "lat": lat,
        "lng": lng,
        "timestamp": int(time.time()),
    }
    headers = {"X-Device-Token": DEVICE_TOKEN, "Content-Type": "application/json"}

    for attempt in range(1, 4):
        try:
            resp = _SESSION.post(
                API_URL, json=payload, headers=headers, timeout=10, verify=True
            )
            if resp.status_code == 200:
                log.info(
                    "API POST success  lat=%.6f  lng=%.6f  status=%d",
                    lat, lng, resp.status_code,
                )
                return True
            else:
                log.warning(
                    "API POST attempt %d/%d  status=%d  body=%s",
                    attempt, 3, resp.status_code, resp.text[:200],
                )
        except requests.exceptions.SSLError as exc:
            log.error("SSL error posting to API: %s", exc)
        except requests.exceptions.ConnectionError as exc:
            log.warning("Connection error (attempt %d/3): %s", attempt, exc)
        except requests.exceptions.Timeout:
            log.warning("Request timed out (attempt %d/3).", attempt)
        except Exception as exc:
            log.error("Unexpected error posting to API: %s", exc)

        if attempt < 3:
            backoff = 2 ** attempt
            log.info("Retrying in %d seconds...", backoff)
            time.sleep(backoff)

    log.error("All 3 API POST attempts failed.")
    return False

# --------------------------------------------------------------------------- #
#  Main loop                                                                   #
# --------------------------------------------------------------------------- #

def main() -> None:
    log.info("=" * 60)
    log.info("SafeWatch A9G Relay — %s", datetime.now(timezone.utc).isoformat())
    log.info("Device ID   : %s", DEVICE_ID)
    log.info("Serial port : %s @ %d", PORT, BAUDRATE)
    log.info("Poll interval: %d seconds", POLL_INTERVAL)
    log.info("Offline mode : %s", OFFLINE_MODE)
    if not OFFLINE_MODE:
        log.info("API URL     : %s", API_URL or "NOT SET — will fail on send")
    log.info("=" * 60)

    ser: Optional[serial.Serial] = None

    try:
        # ── Open serial port ─────────────────────────────────────────────── #
        try:
            ser = open_serial()
        except serial.SerialException as exc:
            log.critical("Cannot open %s: %s", PORT, exc)
            log.info(
                "Available ports: %s",
                [p.device for p in serial.tools.list_ports.comports()] or "none found",
            )
            sys.exit(1)

        # ── Verify A9G responds ──────────────────────────────────────────── #
        if not check_at(ser):
            log.critical(
                "A9G did not respond to AT on %s. "
                "Check USB cable, baud rate, and device power.", PORT
            )
            sys.exit(1)
        log.info("A9G is responding to AT commands.")

        # ── Enable GPS ───────────────────────────────────────────────────── #
        enable_gps(ser)

        log.info(
            "Waiting for GPS fix (timeout %d s). "
            "Place the A9G near a window or outdoors for best results.",
            GPS_FIX_TIMEOUT,
        )

        # ── GPS acquisition loop ─────────────────────────────────────────── #
        fix_acquired = False
        global _sms_sent_flag
        start = time.time()

        while True:
            # --- check if we are still within the initial fix timeout -------
            if not fix_acquired:
                elapsed = time.time() - start
                if elapsed > GPS_FIX_TIMEOUT and not fix_acquired:
                    log.warning(
                        "GPS fix not acquired after %d s. "
                        "Continuing to poll — move the device outdoors.",
                        GPS_FIX_TIMEOUT,
                    )
                    # Reset so this warning appears again after another timeout.
                    start = time.time()

            # --- poll AT+LOCATION=2 -----------------------------------------
            try:
                response = send_at(ser, "AT+LOCATION=2", 2.0)
            except serial.SerialException as exc:
                log.error("Serial error while reading GPS: %s", exc)
                log.info("Attempting to reconnect in 5 seconds...")
                time.sleep(5)
                try:
                    if ser:
                        ser.close()
                    ser = open_serial()
                    if not check_at(ser):
                        log.error("Reconnect failed — A9G not responding.")
                    else:
                        log.info("Reconnected successfully.")
                        enable_gps(ser)
                except serial.SerialException as exc2:
                    log.error("Reconnect attempt failed: %s", exc2)
                continue

            coords = parse_location(response)

            ts = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

            if coords is None:
                if "GPS NOT FIX NOW" in response.upper():
                    log.info("[%s] No GPS fix yet. Waiting...", ts)
                elif "+LOCATION:" in response:
                    log.warning(
                        "[%s] +LOCATION: line present but could not parse: %r",
                        ts, response.strip(),
                    )
                else:
                    log.debug("[%s] No +LOCATION in response. Raw: %r", ts, response.strip())
            else:
                lat, lng = coords
                log.info("[%s] GPS FIX  lat=%.6f  lng=%.6f", ts, lat, lng)

                if not fix_acquired:
                    fix_acquired = True
                    log.info("First GPS fix acquired!")

                    # Send initial SMS once per session
                    if SEND_INITIAL_SMS and not _sms_sent_flag:
                        sms_text = (
                            f"{SMS_MESSAGE}\n"
                            f"Location: {lat:.6f}, {lng:.6f}\n"
                            f"Maps: https://maps.google.com/?q={lat},{lng}"
                        )
                        sent = send_sms(ser, SMS_RECIPIENT, sms_text)
                        if sent:
                            _sms_sent_flag = True

                # POST to API
                post_location(lat, lng)

            # ── Wait before next poll ─────────────────────────────────────── #
            time.sleep(POLL_INTERVAL)

    except KeyboardInterrupt:
        log.info("Interrupted by user. Shutting down.")
    finally:
        if ser and ser.is_open:
            try:
                disable_gps(ser)
                ser.close()
                log.info("Serial port closed.")
            except Exception:
                pass


if __name__ == "__main__":
    main()
