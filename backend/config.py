"""
config.py
---------
Central configuration for the SafeWatch backend.
All sensitive values are read from environment variables or a .env file.
Never hardcode secrets here.
"""

import os
from dotenv import load_dotenv

# Load .env from the backend directory (if present)
load_dotenv(dotenv_path=os.path.join(os.path.dirname(__file__), ".env"))


class Config:
    # ── Flask ──────────────────────────────────────────────────────────────────
    DEBUG: bool = os.getenv("FLASK_DEBUG", "false").lower() == "true"
    HOST: str = os.getenv("HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PORT", "5000"))

    # ── Device authentication ─────────────────────────────────────────────────
    # Shared secret that every A9G relay script must send in the
    # X-Device-Token header.  Generate with:  python -c "import secrets; print(secrets.token_hex(32))"
    DEVICE_SECRET: str = os.getenv("DEVICE_SECRET", "Sumaya@Lazycat@Thala_Safeband")

    # ── Firebase ──────────────────────────────────────────────────────────────
    # Absolute path to your downloaded service-account JSON file.
    FIREBASE_CREDENTIALS_PATH: str = os.getenv(
        "FIREBASE_CREDENTIALS_PATH",
        os.path.join(os.path.dirname(__file__), "serviceAccount.json")
    )
    FIREBASE_DATABASE_URL: str = os.getenv(
        "FIREBASE_DATABASE_URL",
        "https://safe-band-7659f-default-rtdb.asia-southeast1.firebasedatabase.app",
    )

    # ── Location history ─────────────────────────────────────────────────────
    # Set to "true" to write a history entry on every location update.
    ENABLE_HISTORY: bool = os.getenv("ENABLE_HISTORY", "true").lower() == "true"
    # Maximum history entries kept per device (older ones are NOT auto-deleted
    # yet — this constant is used by the cleanup helper if you call it).
    MAX_HISTORY_ENTRIES: int = int(os.getenv("MAX_HISTORY_ENTRIES", "500"))

    # ── Logging ───────────────────────────────────────────────────────────────
    LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO").upper()
    LOG_FILE: str = os.getenv("LOG_FILE", "logs/safewatch_backend.log")

    # ── Waitress (production WSGI) ────────────────────────────────────────────
    WAITRESS_THREADS: int = int(os.getenv("WAITRESS_THREADS", "4"))

    @classmethod
    def validate(cls) -> None:
        """Raise ValueError if any required env var is missing."""
        missing = []
        if not cls.DEVICE_SECRET:
            missing.append("DEVICE_SECRET")
        if not cls.FIREBASE_CREDENTIALS_PATH:
            missing.append("FIREBASE_CREDENTIALS_PATH")
        if not cls.FIREBASE_DATABASE_URL:
            missing.append("FIREBASE_DATABASE_URL")
        if missing:
            raise ValueError(
                f"Missing required environment variables: {', '.join(missing)}\n"
                "Copy .env.example to .env and fill in the values."
            )
