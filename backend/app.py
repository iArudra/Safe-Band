"""
app.py
------
SafeWatch Flask API backend.

Endpoints
---------
GET  /health                  — Liveness probe (unauthenticated)
POST /api/location            — Receive a GPS fix from a relay device
GET  /api/location/<device>   — Read the latest stored fix (token-authenticated)

Authentication
--------------
Every /api/* request must include the header:
    X-Device-Token: <value of DEVICE_SECRET in .env>

Run locally (development)
-------------------------
    python app.py

Run in production (Windows)
---------------------------
    python run_server.py
"""

from __future__ import annotations

import logging
import logging.handlers
import os
import sys
import time
from functools import wraps
from typing import Any, Dict, Tuple

from flask import Flask, jsonify, request, Response

# ── Bootstrap path so we can import sibling modules when run directly ────────
sys.path.insert(0, os.path.dirname(__file__))

from config import Config
import firebase_service as fb

# --------------------------------------------------------------------------- #
#  Logging setup                                                               #
# --------------------------------------------------------------------------- #

os.makedirs("logs", exist_ok=True)

_log_formatter = logging.Formatter(
    "%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
)

_file_handler = logging.handlers.RotatingFileHandler(
    Config.LOG_FILE, maxBytes=5 * 1024 * 1024, backupCount=3
)
_file_handler.setFormatter(_log_formatter)

_console_handler = logging.StreamHandler()
_console_handler.setFormatter(_log_formatter)

logging.basicConfig(
    level=getattr(logging, Config.LOG_LEVEL, logging.INFO),
    handlers=[_file_handler, _console_handler],
)
logger = logging.getLogger(__name__)

# --------------------------------------------------------------------------- #
#  Flask application                                                           #
# --------------------------------------------------------------------------- #

app = Flask(__name__)

# --------------------------------------------------------------------------- #
#  Firebase initialisation — done once at startup                             #
# --------------------------------------------------------------------------- #

def _startup_firebase() -> None:
    try:
        Config.validate()
        fb.init_firebase()
    except (ValueError, RuntimeError) as exc:
        logger.critical("Startup check failed: %s", exc)
        # Do NOT crash immediately — /health should still respond so AWS
        # health checks work while the operator fixes the configuration.
        # All /api/* endpoints will return 503 until Firebase is ready.


_firebase_ready = False

with app.app_context():
    try:
        Config.validate()
        fb.init_firebase()
        _firebase_ready = True
        logger.info("Firebase ready.")
    except Exception as exc:
        logger.error("Firebase not ready at startup: %s", exc)


# --------------------------------------------------------------------------- #
#  Auth decorator                                                              #
# --------------------------------------------------------------------------- #

def require_device_token(f):
    """Reject any request that does not carry the correct X-Device-Token."""
    @wraps(f)
    def decorated(*args, **kwargs):
        provided = request.headers.get("X-Device-Token", "")
        expected = Config.DEVICE_SECRET
        if not expected:
            return _error(503, "Server authentication is not configured.")
        # Constant-time comparison to prevent timing attacks.
        import hmac
        if not hmac.compare_digest(provided.encode(), expected.encode()):
            logger.warning(
                "Rejected unauthorised request from %s", request.remote_addr
            )
            return _error(401, "Unauthorised: invalid or missing X-Device-Token.")
        return f(*args, **kwargs)
    return decorated


def require_firebase(f):
    """Return 503 if Firebase is not initialised."""
    @wraps(f)
    def decorated(*args, **kwargs):
        if not _firebase_ready:
            return _error(503, "Firebase is not initialised. Check server configuration.")
        return f(*args, **kwargs)
    return decorated


# --------------------------------------------------------------------------- #
#  Helpers                                                                     #
# --------------------------------------------------------------------------- #

def _error(status: int, message: str) -> Tuple[Response, int]:
    return jsonify({"ok": False, "error": message}), status


def _validate_coordinates(lat: Any, lng: Any) -> Tuple[float, float]:
    """
    Parse and range-check latitude and longitude.
    Raises ValueError with a descriptive message on invalid input.
    """
    try:
        lat_f = float(lat)
        lng_f = float(lng)
    except (TypeError, ValueError):
        raise ValueError("lat and lng must be numeric values.")
    if not (-90.0 <= lat_f <= 90.0):
        raise ValueError(f"lat={lat_f} is out of range [-90, 90].")
    if not (-180.0 <= lng_f <= 180.0):
        raise ValueError(f"lng={lng_f} is out of range [-180, 180].")
    if lat_f == 0.0 and lng_f == 0.0:
        raise ValueError("lat=0, lng=0 is the Null Island — likely a GPS non-fix.")
    return lat_f, lng_f


def _optional_int(value: Any, name: str) -> int | None:
    if value is None:
        return None
    try:
        v = int(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be an integer.")
    return v


def _optional_float(value: Any, name: str) -> float | None:
    if value is None:
        return None
    try:
        v = float(value)
    except (TypeError, ValueError):
        raise ValueError(f"{name} must be a number.")
    return v


# --------------------------------------------------------------------------- #
#  Routes                                                                      #
# --------------------------------------------------------------------------- #

@app.get("/health")
def health():
    """
    Liveness / health-check probe.
    Returns 200 even if Firebase is not yet initialised so AWS load balancers
    do not replace the instance during a transient config error.
    """
    return jsonify(
        {
            "ok": True,
            "service": "SafeWatch Backend",
            "firebase_ready": _firebase_ready,
            "timestamp": int(time.time()),
        }
    )


@app.post("/api/location")
@require_device_token
@require_firebase
def post_location():
    """
    Receive a GPS fix from the A9G relay script and write it to Firebase.

    Expected JSON body
    ------------------
    {
        "device_id":  "band_001",        # required — alphanumeric + underscore/hyphen
        "lat":        12.9716,           # required — decimal degrees
        "lng":        77.5946,           # required — decimal degrees
        "battery":    85,                # optional — integer 0-100
        "speed":      1.2,              # optional — float, km/h
        "timestamp":  1728497816        # optional — unix epoch; server time used if absent
    }

    Successful response (200)
    -------------------------
    { "ok": true, "device_id": "band_001", "message": "Location updated." }
    """
    data: Dict[str, Any] = request.get_json(silent=True) or {}

    # ── device_id ────────────────────────────────────────────────────────────
    device_id = str(data.get("device_id", "")).strip()
    if not device_id:
        return _error(400, "device_id is required.")
    # Allow only safe characters to avoid path injection in Firebase.
    import re
    if not re.match(r"^[a-zA-Z0-9_\-]{1,64}$", device_id):
        return _error(
            400,
            "device_id must contain only letters, digits, underscores, "
            "or hyphens (max 64 chars).",
        )

    # ── coordinates ───────────────────────────────────────────────────────────
    try:
        lat, lng = _validate_coordinates(data.get("lat"), data.get("lng"))
    except ValueError as exc:
        logger.info("Coordinate validation failed: %s", exc)
        return _error(400, str(exc))

    # ── optional fields ───────────────────────────────────────────────────────
    try:
        battery = _optional_int(data.get("battery"), "battery")
        speed = _optional_float(data.get("speed"), "speed")
        timestamp = _optional_int(data.get("timestamp"), "timestamp")
    except ValueError as exc:
        return _error(400, str(exc))

    if battery is not None and not (0 <= battery <= 100):
        return _error(400, "battery must be between 0 and 100.")
    if speed is not None and speed < 0:
        return _error(400, "speed must be >= 0.")

    # ── write to Firebase ─────────────────────────────────────────────────────
    try:
        fb.update_latest_location(device_id, lat, lng, battery, speed, timestamp)
        if Config.ENABLE_HISTORY:
            fb.append_location_history(device_id, lat, lng, battery, speed, timestamp)
    except Exception as exc:
        logger.error(
            "Firebase write failed for device=%s: %s", device_id, exc
        )
        return _error(502, "Failed to write to Firebase. Try again.")

    return jsonify(
        {
            "ok": True,
            "device_id": device_id,
            "message": "Location updated.",
        }
    )


@app.get("/api/location/<device_id>")
@require_device_token
@require_firebase
def get_location(device_id: str):
    """Return the latest stored location for a device."""
    try:
        data = fb.get_latest_location(device_id)
    except Exception as exc:
        logger.error("Firebase read failed: %s", exc)
        return _error(502, "Failed to read from Firebase.")

    if data is None:
        return _error(404, f"No location data found for device '{device_id}'.")

    return jsonify({"ok": True, "device_id": device_id, "location": data})


# --------------------------------------------------------------------------- #
#  Error handlers                                                              #
# --------------------------------------------------------------------------- #

@app.errorhandler(404)
def not_found(_):
    return _error(404, "Endpoint not found.")


@app.errorhandler(405)
def method_not_allowed(_):
    return _error(405, "Method not allowed.")


@app.errorhandler(500)
def internal_error(exc):
    logger.exception("Unhandled exception: %s", exc)
    return _error(500, "Internal server error.")


# --------------------------------------------------------------------------- #
#  Dev runner                                                                  #
# --------------------------------------------------------------------------- #

if __name__ == "__main__":
    logger.info(
        "Starting Flask development server on %s:%s", Config.HOST, Config.PORT
    )
    logger.warning(
        "This is the development server. Use run_server.py (Waitress) in production."
    )
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
