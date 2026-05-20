'use strict';

const crypto = require('crypto');

const VALID_SIZES_KB = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20];

function validateSize(sizeKb) {
  if (!VALID_SIZES_KB.includes(sizeKb)) {
    throw new Error(`Invalid size: ${sizeKb} KB. Valid values: ${VALID_SIZES_KB}`);
  }
}

function pickFrom(seed, ...values) {
  return values[Math.abs(seed) % values.length];
}

function buildPadString(length) {
  if (length <= 0) return '';
  const unit = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let s = '';
  while (s.length < length) s += unit;
  return s.slice(0, length);
}

function buildAttributes(seed) {
  return {
    color: pickFrom(seed, 'red', 'green', 'blue', 'yellow', 'purple'),
    size: pickFrom(seed + 1, 'small', 'medium', 'large', 'xlarge'),
    weight: seed * 13.7,
    priority: (seed % 5) + 1,
    region: pickFrom(seed + 2, 'APAC', 'EMEA', 'AMER', 'LATAM'),
  };
}

function buildItem(index, resource, padding = '') {
  return {
    id: crypto.randomUUID(),
    index,
    resource,
    name: `Mock ${resource} #${index}`,
    description: `Auto-generated mock record for ${resource}`,
    active: true,
    score: Math.round(Math.random() * 100),
    tags: ['mock', resource, 'generated'],
    createdAt: new Date().toISOString(),
    attributes: buildAttributes(index),
    padding,
  };
}

function buildEnvelope(sizeKb, method, resource) {
  const meta = {
    server: 'MockAPIServer/1.0',
    timestamp: new Date().toISOString(),
    method: method.toUpperCase(),
    resource,
    targetSizeKb: sizeKb,
    requestId: crypto.randomUUID(),
    status: '200 OK',
    contentType: 'application/json',
  };
  const items = [];
  for (let i = 1; i <= 5; i++) items.push(buildItem(i, resource));
  return { metadata: meta, count: items.length, items };
}

function padJson(envelope, targetBytes) {
  let base = JSON.stringify(envelope, null, 2);
  if (Buffer.byteLength(base, 'utf8') >= targetBytes) return base;

  const last = envelope.items[envelope.items.length - 1];
  let padNeeded = targetBytes - Buffer.byteLength(base, 'utf8');
  if (padNeeded > 0) last.padding = buildPadString(padNeeded);

  let result = JSON.stringify(envelope, null, 2);
  let diff = targetBytes - Buffer.byteLength(result, 'utf8');
  if (diff > 0) {
    last.padding = (last.padding || '') + buildPadString(diff);
    result = JSON.stringify(envelope, null, 2);
  } else if (diff < 0) {
    const trimTo = Math.max(0, (last.padding || '').length + diff);
    last.padding = (last.padding || '').slice(0, trimTo);
    result = JSON.stringify(envelope, null, 2);
  }
  return result;
}

function escapeXml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function toXmlValue(key, val) {
  if (Array.isArray(val)) {
    return val.map((v) => `<${key}>${escapeXml(v)}</${key}>`).join('');
  }
  if (val !== null && typeof val === 'object') {
    return objectToXml(val);
  }
  if (typeof val === 'boolean') return `<${key}>${val}</${key}>`;
  return `<${key}>${escapeXml(val)}</${key}>`;
}

function objectToXml(obj) {
  return Object.entries(obj)
    .map(([k, v]) => toXmlValue(k, v))
    .join('');
}

function serializeXml(envelope) {
  const inner = objectToXml(envelope);
  return `<?xml version="1.0" encoding="UTF-8"?>\n<MockResponse>${inner}</MockResponse>`;
}

function padXml(envelope, targetBytes) {
  let base = serializeXml(envelope);
  if (Buffer.byteLength(base, 'utf8') >= targetBytes) return base;

  const last = envelope.items[envelope.items.length - 1];
  let padNeeded = targetBytes - Buffer.byteLength(base, 'utf8');
  last.padding = buildPadString(padNeeded);

  let result = serializeXml(envelope);
  let diff = targetBytes - Buffer.byteLength(result, 'utf8');
  if (diff > 0) {
    last.padding += buildPadString(diff);
    result = serializeXml(envelope);
  } else if (diff < 0) {
    const trimTo = Math.max(0, last.padding.length + diff);
    last.padding = last.padding.slice(0, trimTo);
    result = serializeXml(envelope);
  }
  return result;
}

function padText(envelope, targetBytes) {
  const meta = envelope.metadata;
  const lines = [
    '=== Mock API Server Response ===',
    `server      : ${meta.server}`,
    `timestamp   : ${meta.timestamp}`,
    `method      : ${meta.method}`,
    `resource    : ${meta.resource}`,
    `targetSizeKb: ${meta.targetSizeKb}`,
    `requestId   : ${meta.requestId}`,
    `status      : ${meta.status}`,
    `count       : ${envelope.count}`,
    '---',
  ];
  for (const item of envelope.items) {
    lines.push('[item]');
    for (const [k, v] of Object.entries(item)) {
      if (k !== 'padding') lines.push(`  ${k} = ${v}`);
    }
  }
  lines.push('[padding]');
  const base = lines.join('\n') + '\n';
  let padNeeded = targetBytes - Buffer.byteLength(base, 'utf8');
  padNeeded = Math.max(0, padNeeded - 1);
  return base + buildPadString(padNeeded) + '\n';
}

function padHtml(envelope, targetBytes, method, resource) {
  const meta = envelope.metadata;
  let rows = '';
  for (const item of envelope.items) {
    rows += `<tr><td>${item.id}</td><td>${item.index}</td><td>${item.name}</td>`;
    rows += `<td>${item.description}</td><td>${item.active}</td><td>${item.score}</td>`;
    rows += `<td>${item.createdAt}</td></tr>\n`;
  }
  const template = `<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>Mock API Response — ${resource}</title>
<style>body{font-family:sans-serif;background:#0d1117;color:#e6edf3;margin:24px}h1{color:#58a6ff;font-size:1.4rem;margin-bottom:8px}.meta{font-size:.85rem;color:#8b949e;margin-bottom:16px}table{border-collapse:collapse;width:100%}th{background:#21262d;color:#58a6ff;padding:8px 12px;text-align:left;border:1px solid #30363d}td{padding:7px 12px;border:1px solid #30363d;font-size:.85rem}tr:nth-child(even){background:#161b22}</style></head>
<body>
<h1>Mock API Response</h1>
<div class="meta"><strong>${method.toUpperCase()}</strong> /${resource} &nbsp;|&nbsp; requestId: ${meta.requestId} &nbsp;|&nbsp; ${meta.timestamp}</div>
<table>
<thead><tr><th>ID</th><th>#</th><th>Name</th><th>Description</th><th>Active</th><th>Score</th><th>Created At</th></tr></thead>
<tbody>
${rows}</tbody>
</table>
<!-- [padding] `;
  const tail = ' -->\n</body>\n</html>';
  const padNeeded = Math.max(0, targetBytes - Buffer.byteLength(template + tail, 'utf8'));
  return template + buildPadString(padNeeded) + tail;
}

function generate(sizeKb, format, method, resource) {
  validateSize(sizeKb);
  const targetBytes = sizeKb * 1024;
  const envelope = buildEnvelope(sizeKb, method, resource);
  const fmt = (format || 'json').toLowerCase();

  switch (fmt) {
    case 'xml':
      return padXml(envelope, targetBytes);
    case 'text':
      return padText(envelope, targetBytes);
    case 'html':
      return padHtml(envelope, targetBytes, method, resource);
    default:
      return padJson(envelope, targetBytes);
  }
}

module.exports = { VALID_SIZES_KB, generate, validateSize };
