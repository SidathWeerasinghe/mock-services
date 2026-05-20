"""
REST controller exposing GET / POST / PUT / DELETE endpoints.

URL pattern:
  /api/{resource}
  /api/{resource}/{id}   (PUT, DELETE, GET)

Query parameters:
  size   : 1,2,3,4,5,6,7,8,9,10,15,20 (KB)  — default 1
  format : json | xml | text | html           — default json

Example calls:
  GET    http://localhost:8080/api/orders?size=5&format=json
  GET    http://localhost:8080/api/orders?size=10&format=xml
  POST   http://localhost:8080/api/users?size=2&format=json
  PUT    http://localhost:8080/api/users/42?size=3&format=xml
  DELETE http://localhost:8080/api/products/99?size=1&format=json
"""
from flask import Blueprint, request, Response

from app.service.payload_generator import generate

rest_bp = Blueprint("rest", __name__, url_prefix="/api")

MIME_TYPES = {
    "json": "application/json",
    "xml":  "application/xml",
    "text": "text/plain",
    "html": "text/html",
}


def _build_response(size: int, fmt: str, method: str, resource: str) -> Response:
    payload = generate(size, fmt, method, resource)
    mime = MIME_TYPES.get(fmt.lower(), "application/json")
    byte_len = len(payload.encode("utf-8"))

    resp = Response(payload, status=200, mimetype=mime)
    resp.headers["X-Mock-Size-KB"] = str(size)
    resp.headers["X-Mock-Format"] = fmt.lower()
    resp.headers["X-Mock-Method"] = method
    resp.headers["X-Mock-Resource"] = resource
    resp.headers["X-Payload-Bytes"] = str(byte_len)
    return resp


# ─── GET ──────────────────────────────────────────────────────────────────────

@rest_bp.route("/<resource>", methods=["GET"])
def get_collection(resource: str):
    """GET /api/{resource} — list resources"""
    size = request.args.get("size", 1, type=int)
    fmt = request.args.get("format", "json")
    return _build_response(size, fmt, "GET", resource)


@rest_bp.route("/<resource>/<id>", methods=["GET"])
def get_one(resource: str, id: str):
    """GET /api/{resource}/{id} — get single resource"""
    size = request.args.get("size", 1, type=int)
    fmt = request.args.get("format", "json")
    return _build_response(size, fmt, "GET", f"{resource}/{id}")


# ─── POST ─────────────────────────────────────────────────────────────────────

@rest_bp.route("/<resource>", methods=["POST"])
def create(resource: str):
    """POST /api/{resource} — create resource"""
    size = request.args.get("size", 1, type=int)
    fmt = request.args.get("format", "json")
    return _build_response(size, fmt, "POST", resource)


# ─── PUT ──────────────────────────────────────────────────────────────────────

@rest_bp.route("/<resource>/<id>", methods=["PUT"])
def update(resource: str, id: str):
    """PUT /api/{resource}/{id} — update resource"""
    size = request.args.get("size", 1, type=int)
    fmt = request.args.get("format", "json")
    return _build_response(size, fmt, "PUT", f"{resource}/{id}")


# ─── DELETE ───────────────────────────────────────────────────────────────────

@rest_bp.route("/<resource>/<id>", methods=["DELETE"])
def delete(resource: str, id: str):
    """DELETE /api/{resource}/{id} — delete resource"""
    size = request.args.get("size", 1, type=int)
    fmt = request.args.get("format", "json")
    return _build_response(size, fmt, "DELETE", f"{resource}/{id}")
