"""
Global exception handlers — returns structured JSON error responses
for bad request parameters (invalid size, unsupported format, etc.).

Equivalent of GlobalExceptionHandler.java.
"""
from datetime import datetime, timezone

from flask import jsonify


def register_error_handlers(app):
    """Register global error handlers on the Flask app."""

    @app.errorhandler(ValueError)
    def handle_value_error(exc):
        body = {
            "status": 400,
            "error": "Bad Request",
            "message": str(exc),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "hint": "Valid sizes (KB): 1,2,3,4,5,6,7,8,9,10,15,20 | formats: json, xml",
        }
        return jsonify(body), 400

    @app.errorhandler(Exception)
    def handle_general(exc):
        body = {
            "status": 500,
            "error": "Internal Server Error",
            "message": str(exc),
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        return jsonify(body), 500
