"""
run_server.py
-------------
Production entry-point.  Uses Waitress (Windows-compatible WSGI server).

Usage
-----
    python run_server.py

Waitress does not use Flask's dev server, does not reload on changes,
and handles concurrent requests via threads.
"""

import logging
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

from waitress import serve
from config import Config
from app import app

logger = logging.getLogger(__name__)

if __name__ == "__main__":
    Config.validate()   # Fail fast if secrets are missing.

    logger.info(
        "Starting Waitress on %s:%s with %d threads",
        Config.HOST,
        Config.PORT,
        Config.WAITRESS_THREADS,
    )
    serve(
        app,
        host=Config.HOST,
        port=Config.PORT,
        threads=Config.WAITRESS_THREADS,
        # Trust X-Forwarded-For from load-balancers/reverse proxies.
        trusted_proxy="*",
        trusted_proxy_headers="x-forwarded-for x-forwarded-proto",
    )
