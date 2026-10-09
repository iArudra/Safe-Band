"""
firebase_service.py
--------------------
Thin wrapper around the Firebase Admin SDK.

Responsibilities
----------------
- Initialise the Admin SDK exactly once (singleton pattern).
- Provide typed helper functions for every database operation the
  backend needs: write latest location, write history entry, write SOS alert.
- Never store or log credential values.
- Raise clear exceptions so app.py can return the right HTTP status.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Any, Dict, Optional

import firebase_admin
from firebase_admin import credentials, db

from config import Config

logger = logging.getLogger(__name__)

# --------------------------------------------------------------------------- #
#  SDK initialisation                                                          #
# --------------------------------------------------------------------------- #

_initialized = False


def init_firebase() -> None:
    """Initialise the Firebase Admin SDK.  Safe to call multiple times."""
    global _initialized
    if _initialized:
        return

    cred_path = Config.FIREBASE_CREDENTIALS_PATH
    db_url = Config.FIREBASE_DATABASE_URL

    if not cred_path:
        raise RuntimeError(
            "FIREBASE_CREDENTIALS_PATH is not set. "
            "Download your service-account JSON from the Firebase console and "
            "set the path in your .env file."
        )

    try:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred, {"databaseURL": db_url})
        _initialized = True
        logger.info("Firebase Admin SDK initialised (database: %s)", db_url)
    except Exception as exc:
        logger.critical("Failed to initialise Firebase: %s", exc)
        raise


# --------------------------------------------------------------------------- #
#  Helper — build the canonical location object                                #
# --------------------------------------------------------------------------- #

def _build_location_payload(
    lat: float,
    lng: float,
    battery: Optional[int],
    speed: Optional[float],
    timestamp: Optional[int],
) -> Dict[str, Any]:
    """Return the dict that will be written to Firebase."""
    now_ts = int(time.time())
    payload: Dict[str, Any] = {
        "lat": lat,
        "lng": lng,
        "timestamp": timestamp if timestamp is not None else now_ts,
        "updated_at": datetime.now(timezone.utc).isoformat(),
    }
    # Only include optional fields when the hardware actually provides them.
    if battery is not None:
        payload["battery"] = battery
    if speed is not None:
        payload["speed"] = speed
    return payload


# --------------------------------------------------------------------------- #
#  Public API                                                                  #
# --------------------------------------------------------------------------- #

def update_latest_location(
    device_id: str,
    lat: float,
    lng: float,
    battery: Optional[int] = None,
    speed: Optional[float] = None,
    timestamp: Optional[int] = None,
) -> None:
    """
    Overwrite the `devices/{device_id}/location` node with the latest fix.
    This is the value the Android app listens to in real time.
    """
    payload = _build_location_payload(lat, lng, battery, speed, timestamp)
    ref = db.reference(f"devices/{device_id}/location")
    ref.set(payload)
    logger.info(
        "Updated location for device=%s  lat=%.6f lng=%.6f", device_id, lat, lng
    )


def append_location_history(
    device_id: str,
    lat: float,
    lng: float,
    battery: Optional[int] = None,
    speed: Optional[float] = None,
    timestamp: Optional[int] = None,
) -> str:
    """
    Push a new entry to `devices/{device_id}/history`.
    Returns the Firebase push key for the new record.
    """
    payload = _build_location_payload(lat, lng, battery, speed, timestamp)
    ref = db.reference(f"devices/{device_id}/history")
    new_ref = ref.push(payload)
    logger.debug(
        "History entry %s added for device=%s", new_ref.key, device_id
    )
    return new_ref.key  # type: ignore[return-value]


def write_sos_alert(
    device_id: str,
    lat: float,
    lng: float,
    alert_id: Optional[str] = None,
) -> str:
    """
    Write an SOS alert under `devices/{device_id}/alerts`.
    If alert_id is None a push key is used.
    Returns the alert key.
    """
    payload = {
        "type": "SOS",
        "status": "pending",
        "lat": lat,
        "lng": lng,
        "timestamp": int(time.time()),
    }
    if alert_id:
        ref = db.reference(f"devices/{device_id}/alerts/{alert_id}")
        ref.set(payload)
        return alert_id
    else:
        ref = db.reference(f"devices/{device_id}/alerts")
        new_ref = ref.push(payload)
        logger.info(
            "SOS alert %s written for device=%s", new_ref.key, device_id
        )
        return new_ref.key  # type: ignore[return-value]


def get_latest_location(device_id: str) -> Optional[Dict[str, Any]]:
    """Fetch the current location record.  Returns None if not found."""
    ref = db.reference(f"devices/{device_id}/location")
    return ref.get()
