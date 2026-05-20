"""python -m app — start the multi-port Mock API Server."""
from app.main import create_app
from app.server_runner import run_servers

run_servers(create_app())
