"""Shared Flask-Sock instance for all WebSocket routes."""
from flask_sock import Sock

sock = Sock()
