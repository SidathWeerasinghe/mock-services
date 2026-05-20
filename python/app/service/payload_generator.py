"""
Generates mock payloads of an exact target size (in kilobytes).

Supported formats: json (default), xml, text, html.
Valid sizes (KB): 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20

Strategy:
  1. Build a structured envelope (metadata + items list).
  2. Measure its serialised byte length.
  3. Pad a dedicated padding field/block with ASCII characters until
     the total byte count reaches exactly targetKb * 1024.
"""
import json
import random
import uuid
from collections import OrderedDict
from datetime import datetime, timezone

from dicttoxml import dicttoxml

VALID_SIZES_KB = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20]


def validate_size(size_kb: int) -> None:
    """Validates that the requested size is one of the allowed values."""
    if size_kb not in VALID_SIZES_KB:
        raise ValueError(
            f"Invalid size: {size_kb} KB. Valid values: {VALID_SIZES_KB}"
        )


def _pick_from(seed: int, *values: str) -> str:
    return values[abs(seed) % len(values)]


def _build_attributes(seed: int) -> OrderedDict:
    """Nested attributes block to add structural depth."""
    return OrderedDict([
        ("color", _pick_from(seed, "red", "green", "blue", "yellow", "purple")),
        ("size", _pick_from(seed + 1, "small", "medium", "large", "xlarge")),
        ("weight", seed * 13.7),
        ("priority", seed % 5 + 1),
        ("region", _pick_from(seed + 2, "APAC", "EMEA", "AMER", "LATAM")),
    ])


def _build_item(index: int, resource: str, padding: str = "") -> OrderedDict:
    """Builds a single mock item record."""
    return OrderedDict([
        ("id", str(uuid.uuid4())),
        ("index", index),
        ("resource", resource),
        ("name", f"Mock {resource} #{index}"),
        ("description", f"Auto-generated mock record for {resource}"),
        ("active", True),
        ("score", round(random.random() * 100)),
        ("tags", ["mock", resource, "generated"]),
        ("createdAt", datetime.now(timezone.utc).isoformat()),
        ("attributes", _build_attributes(index)),
        ("padding", padding),
    ])


def _build_envelope(size_kb: int, method: str, resource: str) -> OrderedDict:
    """Builds a structured dict used as the serialisation source."""
    meta = OrderedDict([
        ("server", "MockAPIServer/1.0"),
        ("timestamp", datetime.now(timezone.utc).isoformat()),
        ("method", method.upper()),
        ("resource", resource),
        ("targetSizeKb", size_kb),
        ("requestId", str(uuid.uuid4())),
        ("status", "200 OK"),
        ("contentType", "application/json"),
    ])

    items = [_build_item(i, resource) for i in range(1, 6)]

    return OrderedDict([
        ("metadata", meta),
        ("count", len(items)),
        ("items", items),
    ])


def _build_pad_string(length: int) -> str:
    """Generates a repeating ASCII pad string of exactly `length` characters."""
    if length <= 0:
        return ""
    unit = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    repeat = (length // len(unit)) + 1
    return (unit * repeat)[:length]


# ── JSON padding ──────────────────────────────────────────────────────────────

def _pad_json(envelope: OrderedDict, target_bytes: int) -> str:
    base = json.dumps(envelope, indent=2, ensure_ascii=False)
    current = len(base.encode("utf-8"))

    if current >= target_bytes:
        return base

    items = envelope["items"]
    last_item = items[-1]

    pad_needed = target_bytes - current
    if pad_needed > 0:
        last_item["padding"] = _build_pad_string(pad_needed)

    # Fine-tune: iterate to exact size
    result = json.dumps(envelope, indent=2, ensure_ascii=False)
    result_len = len(result.encode("utf-8"))
    diff = target_bytes - result_len

    if diff > 0:
        existing_pad = last_item.get("padding", "")
        last_item["padding"] = existing_pad + _build_pad_string(diff)
        result = json.dumps(envelope, indent=2, ensure_ascii=False)
    elif diff < 0:
        existing_pad = last_item.get("padding", "")
        trim_to = max(0, len(existing_pad) + diff)
        last_item["padding"] = existing_pad[:trim_to]
        result = json.dumps(envelope, indent=2, ensure_ascii=False)

    return result


# ── XML padding ───────────────────────────────────────────────────────────────

def _serialize_xml(envelope: OrderedDict) -> str:
    """Serializes the envelope dict to XML with root element MockResponse."""
    xml_bytes = dicttoxml(envelope, custom_root="MockResponse", attr_type=False)
    return xml_bytes.decode("utf-8")


def _pad_xml(envelope: OrderedDict, target_bytes: int) -> str:
    base = _serialize_xml(envelope)
    current = len(base.encode("utf-8"))

    if current >= target_bytes:
        return base

    items = envelope["items"]
    last_item = items[-1]

    pad_needed = target_bytes - current
    last_item["padding"] = _build_pad_string(pad_needed)

    result = _serialize_xml(envelope)
    result_len = len(result.encode("utf-8"))
    diff = target_bytes - result_len

    if diff > 0:
        existing = last_item.get("padding", "")
        last_item["padding"] = existing + _build_pad_string(diff)
        result = _serialize_xml(envelope)
    elif diff < 0:
        existing = last_item.get("padding", "")
        trim_to = max(0, len(existing) + diff)
        last_item["padding"] = existing[:trim_to]
        result = _serialize_xml(envelope)

    return result


# ── Plain-text padding ────────────────────────────────────────────────────────

def _pad_text(envelope: OrderedDict, target_bytes: int) -> str:
    meta = envelope["metadata"]
    items = envelope["items"]

    lines = [
        "=== Mock API Server Response ===",
        f"server      : {meta['server']}",
        f"timestamp   : {meta['timestamp']}",
        f"method      : {meta['method']}",
        f"resource    : {meta['resource']}",
        f"targetSizeKb: {meta['targetSizeKb']}",
        f"requestId   : {meta['requestId']}",
        f"status      : {meta['status']}",
        f"count       : {len(items)}",
        "---",
    ]

    for item in items:
        lines.append("[item]")
        for k, v in item.items():
            if k != "padding":
                lines.append(f"  {k} = {v}")

    lines.append("[padding]")
    base = "\n".join(lines) + "\n"
    current = len(base.encode("utf-8"))
    pad_needed = target_bytes - current
    # account for the newline after padding
    pad_needed = max(0, pad_needed - 1)
    return base + _build_pad_string(pad_needed) + "\n"


# ── HTML padding ──────────────────────────────────────────────────────────────

def _pad_html(envelope: OrderedDict, target_bytes: int,
              method: str, resource: str) -> str:
    meta = envelope["metadata"]
    items = envelope["items"]

    # Build table rows
    rows = ""
    for item in items:
        rows += "<tr>"
        rows += f"<td>{item['id']}</td>"
        rows += f"<td>{item['index']}</td>"
        rows += f"<td>{item['name']}</td>"
        rows += f"<td>{item['description']}</td>"
        rows += f"<td>{item['active']}</td>"
        rows += f"<td>{item['score']}</td>"
        rows += f"<td>{item['createdAt']}</td>"
        rows += "</tr>\n"

    template = (
        '<!DOCTYPE html>\n'
        '<html lang="en">\n'
        '<head><meta charset="UTF-8">'
        f'<title>Mock API Response — {resource}</title>\n'
        '<style>'
        'body{font-family:sans-serif;background:#0d1117;color:#e6edf3;margin:24px}'
        'h1{color:#58a6ff;font-size:1.4rem;margin-bottom:8px}'
        '.meta{font-size:.85rem;color:#8b949e;margin-bottom:16px}'
        'table{border-collapse:collapse;width:100%}'
        'th{background:#21262d;color:#58a6ff;padding:8px 12px;text-align:left;border:1px solid #30363d}'
        'td{padding:7px 12px;border:1px solid #30363d;font-size:.85rem}'
        'tr:nth-child(even){background:#161b22}'
        '</style></head>\n'
        '<body>\n'
        '<h1>Mock API Response</h1>\n'
        '<div class="meta">'
        f'<strong>{method.upper()}</strong> /{resource}'
        f' &nbsp;|&nbsp; requestId: {meta["requestId"]}'
        f' &nbsp;|&nbsp; {meta["timestamp"]}'
        '</div>\n'
        '<table>\n'
        '<thead><tr>'
        '<th>ID</th><th>#</th><th>Name</th><th>Description</th>'
        '<th>Active</th><th>Score</th><th>Created At</th>'
        '</tr></thead>\n'
        f'<tbody>\n{rows}</tbody>\n'
        '</table>\n'
        '<!-- [padding] '
    )
    tail = ' -->\n</body>\n</html>'

    base_len = len((template + tail).encode("utf-8"))
    pad_needed = max(0, target_bytes - base_len)
    return template + _build_pad_string(pad_needed) + tail


# ── Public API ────────────────────────────────────────────────────────────────

def generate(size_kb: int, fmt: str, method: str, resource: str) -> str:
    """
    Generate a payload.

    Args:
        size_kb:  Target size in KB (must be one of VALID_SIZES_KB).
        fmt:      'json', 'xml', 'text', or 'html'.
        method:   HTTP method label (GET / POST / PUT / DELETE).
        resource: Logical resource name (e.g. 'orders', 'users').

    Returns:
        Raw string payload of approximately size_kb KB.
    """
    validate_size(size_kb)
    target_bytes = size_kb * 1024

    envelope = _build_envelope(size_kb, method, resource)

    fmt_lower = fmt.lower() if fmt else "json"

    if fmt_lower == "xml":
        return _pad_xml(envelope, target_bytes)
    elif fmt_lower == "text":
        return _pad_text(envelope, target_bytes)
    elif fmt_lower == "html":
        return _pad_html(envelope, target_bytes, method, resource)
    else:
        return _pad_json(envelope, target_bytes)
