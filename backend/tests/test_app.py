"""
tests/test_app.py
-----------------
Automated tests for the SafeWatch Flask API.

What is tested automatically (no hardware, no Firebase, no AWS needed)
-----------------------------------------------------------------------
- /health endpoint
- POST /api/location — authentication, field validation, coordinate checks
- GET  /api/location/<device> — authentication
- Error handlers (404, 405)

What requires real infrastructure
-----------------------------------
- Actual Firebase write/read  → needs FIREBASE_CREDENTIALS_PATH + live project
- Actual A9G serial comms     → needs physical hardware on COM3
- AWS end-to-end              → needs deployed EC2 instance

Run
---
    cd backend
    pytest tests/ -v
"""

import json
import os
import sys

import pytest

# Make the backend package importable.
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

# ── Provide stub env vars so Config.validate() is satisfied when running
#    tests without a real .env.
os.environ.setdefault("DEVICE_SECRET", "test-secret-token-abc123")
os.environ.setdefault("FIREBASE_CREDENTIALS_PATH", "/fake/path/serviceAccount.json")
os.environ.setdefault("FIREBASE_DATABASE_URL",
                      "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app")


# ── Patch firebase_service so tests never touch real Firebase ────────────── #
import unittest.mock as mock

# Patch init_firebase and update_latest_location before app is imported.
_firebase_patch = mock.patch.dict(
    "sys.modules",
    {
        "firebase_admin": mock.MagicMock(),
        "firebase_admin.credentials": mock.MagicMock(),
        "firebase_admin.db": mock.MagicMock(),
    },
)
_firebase_patch.start()

import firebase_service  # noqa: E402
firebase_service._initialized = True   # pretend SDK is ready
firebase_service.init_firebase = mock.MagicMock()
firebase_service.update_latest_location = mock.MagicMock()
firebase_service.append_location_history = mock.MagicMock()
firebase_service.get_latest_location = mock.MagicMock(return_value={
    "lat": 12.9716,
    "lng": 77.5946,
    "timestamp": 1728497816,
    "updated_at": "2026-10-09T16:00:00+00:00",
})

import app as flask_app  # noqa: E402
flask_app._firebase_ready = True       # pretend Firebase is initialised

# --------------------------------------------------------------------------- #
#  Fixtures                                                                    #
# --------------------------------------------------------------------------- #

VALID_TOKEN = "test-secret-token-abc123"
GOOD_HEADERS = {"X-Device-Token": VALID_TOKEN, "Content-Type": "application/json"}


@pytest.fixture
def client():
    flask_app.app.config["TESTING"] = True
    with flask_app.app.test_client() as c:
        yield c


# --------------------------------------------------------------------------- #
#  /health                                                                     #
# --------------------------------------------------------------------------- #

class TestHealth:
    def test_health_ok(self, client):
        r = client.get("/health")
        assert r.status_code == 200
        data = r.get_json()
        assert data["ok"] is True
        assert data["service"] == "SafeWatch Backend"
        assert "timestamp" in data

    def test_health_no_auth_required(self, client):
        # /health must work without X-Device-Token
        r = client.get("/health")
        assert r.status_code == 200


# --------------------------------------------------------------------------- #
#  POST /api/location — authentication                                        #
# --------------------------------------------------------------------------- #

class TestPostLocationAuth:
    def test_missing_token_returns_401(self, client):
        r = client.post(
            "/api/location",
            json={"device_id": "band_001", "lat": 12.9716, "lng": 77.5946},
        )
        assert r.status_code == 401

    def test_wrong_token_returns_401(self, client):
        r = client.post(
            "/api/location",
            json={"device_id": "band_001", "lat": 12.9716, "lng": 77.5946},
            headers={"X-Device-Token": "wrong-token", "Content-Type": "application/json"},
        )
        assert r.status_code == 401

    def test_correct_token_accepted(self, client):
        r = client.post(
            "/api/location",
            json={"device_id": "band_001", "lat": 12.9716, "lng": 77.5946},
            headers=GOOD_HEADERS,
        )
        assert r.status_code == 200
        assert r.get_json()["ok"] is True


# --------------------------------------------------------------------------- #
#  POST /api/location — field validation                                      #
# --------------------------------------------------------------------------- #

class TestPostLocationValidation:
    def _post(self, client, body):
        return client.post("/api/location", json=body, headers=GOOD_HEADERS)

    def test_missing_device_id(self, client):
        r = self._post(client, {"lat": 12.9716, "lng": 77.5946})
        assert r.status_code == 400
        assert "device_id" in r.get_json()["error"].lower()

    def test_missing_lat(self, client):
        r = self._post(client, {"device_id": "band_001", "lng": 77.5946})
        assert r.status_code == 400

    def test_missing_lng(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": 12.9716})
        assert r.status_code == 400

    def test_non_numeric_lat(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": "abc", "lng": 77.5946})
        assert r.status_code == 400

    def test_lat_out_of_range_high(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": 91.0, "lng": 77.5946})
        assert r.status_code == 400

    def test_lat_out_of_range_low(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": -91.0, "lng": 77.5946})
        assert r.status_code == 400

    def test_lng_out_of_range(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": 12.9716, "lng": 181.0})
        assert r.status_code == 400

    def test_null_island_rejected(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": 0.0, "lng": 0.0})
        assert r.status_code == 400

    def test_invalid_device_id_chars(self, client):
        r = self._post(client, {"device_id": "../etc/passwd", "lat": 12.9716, "lng": 77.5946})
        assert r.status_code == 400

    def test_battery_out_of_range(self, client):
        r = self._post(client, {
            "device_id": "band_001", "lat": 12.9716, "lng": 77.5946, "battery": 110
        })
        assert r.status_code == 400

    def test_negative_speed_rejected(self, client):
        r = self._post(client, {
            "device_id": "band_001", "lat": 12.9716, "lng": 77.5946, "speed": -1.0
        })
        assert r.status_code == 400

    def test_optional_fields_accepted(self, client):
        r = self._post(client, {
            "device_id": "band_001",
            "lat": 12.9716,
            "lng": 77.5946,
            "battery": 80,
            "speed": 1.5,
            "timestamp": 1728497816,
        })
        assert r.status_code == 200

    def test_valid_negative_coordinates(self, client):
        # South America — valid negative coords
        r = self._post(client, {"device_id": "band_001", "lat": -23.5505, "lng": -46.6333})
        assert r.status_code == 200

    def test_response_contains_device_id(self, client):
        r = self._post(client, {"device_id": "band_001", "lat": 12.9716, "lng": 77.5946})
        assert r.get_json()["device_id"] == "band_001"


# --------------------------------------------------------------------------- #
#  GET /api/location/<device>                                                 #
# --------------------------------------------------------------------------- #

class TestGetLocation:
    def test_missing_token_returns_401(self, client):
        r = client.get("/api/location/band_001")
        assert r.status_code == 401

    def test_valid_request_returns_200(self, client):
        r = client.get(
            "/api/location/band_001",
            headers={"X-Device-Token": VALID_TOKEN},
        )
        assert r.status_code == 200
        data = r.get_json()
        assert data["ok"] is True
        assert "location" in data

    def test_unknown_device_returns_404(self, client):
        firebase_service.get_latest_location.return_value = None
        r = client.get(
            "/api/location/nonexistent",
            headers={"X-Device-Token": VALID_TOKEN},
        )
        assert r.status_code == 404
        # restore
        firebase_service.get_latest_location.return_value = {
            "lat": 12.9716, "lng": 77.5946, "timestamp": 1728497816
        }


# --------------------------------------------------------------------------- #
#  Error handlers                                                              #
# --------------------------------------------------------------------------- #

class TestErrorHandlers:
    def test_404(self, client):
        r = client.get("/nonexistent-endpoint")
        assert r.status_code == 404

    def test_405_wrong_method(self, client):
        r = client.get("/api/location", headers=GOOD_HEADERS)
        assert r.status_code == 405


# --------------------------------------------------------------------------- #
#  GPS parser (a9g_relay.py)                                                  #
# --------------------------------------------------------------------------- #

class TestGpsParser:
    """Unit tests for parse_location() — no serial port needed."""

    def setup_method(self):
        import a9g_relay
        self.parse = a9g_relay.parse_location

    def test_no_fix_returns_none(self):
        response = "\r\n+LOCATION: GPS NOT FIX NOW\r\n\r\nOK\r\n"
        assert self.parse(response) is None

    def test_bare_coordinates(self):
        response = "\r\n+LOCATION: 12.971600,77.594600\r\n\r\nOK\r\n"
        result = self.parse(response)
        assert result is not None
        lat, lng = result
        assert abs(lat - 12.9716) < 0.0001
        assert abs(lng - 77.5946) < 0.0001

    def test_prefixed_coordinates(self):
        response = "\r\n+LOCATION: 2,12.971600,77.594600\r\n\r\nOK\r\n"
        result = self.parse(response)
        assert result is not None
        lat, lng = result
        assert abs(lat - 12.9716) < 0.0001

    def test_extended_fields(self):
        response = "\r\n+LOCATION: 2,12.971600,77.594600,0.00,0.00\r\n\r\nOK\r\n"
        result = self.parse(response)
        assert result is not None
        lat, lng = result
        assert abs(lat - 12.9716) < 0.0001

    def test_null_island_returns_none(self):
        response = "\r\n+LOCATION: 0.000000,0.000000\r\n\r\nOK\r\n"
        assert self.parse(response) is None

    def test_negative_lat_lng(self):
        response = "\r\n+LOCATION: -23.550500,-46.633300\r\n\r\nOK\r\n"
        result = self.parse(response)
        assert result is not None
        lat, lng = result
        assert abs(lat - (-23.5505)) < 0.0001

    def test_empty_response_returns_none(self):
        assert self.parse("") is None

    def test_malformed_response_returns_none(self):
        assert self.parse("\r\n+LOCATION: ABCDEFG\r\n") is None
