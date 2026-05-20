'use strict';

const { generate } = require('../payload/generator');
const { handlePayloadError } = require('./errors');

function mimeForFormat(format) {
  switch ((format || 'json').toLowerCase()) {
    case 'xml': return 'application/xml';
    case 'text': return 'text/plain';
    case 'html': return 'text/html';
    default: return 'application/json';
  }
}

function writePayloadResponse(res, size, format, method, resource) {
  try {
    const payload = generate(size, format, method, resource);
    const bytes = Buffer.byteLength(payload, 'utf8');
    res.set({
      'Content-Type': mimeForFormat(format),
      'X-Mock-Size-KB': String(size),
      'X-Mock-Format': (format || 'json').toLowerCase(),
      'X-Mock-Method': method,
      'X-Mock-Resource': resource,
      'X-Payload-Bytes': String(bytes),
    });
    res.status(200).send(payload);
  } catch (err) {
    if (!handlePayloadError(res, err)) throw err;
  }
}

function parseAPIPath(path) {
  const p = (path || '').replace(/^\//, '');
  if (!p) return null;
  const parts = p.split('/').filter(Boolean);
  if (parts.length === 1) return { resource: parts[0], id: null };
  if (parts.length === 2) return { resource: parts[0], id: parts[1] };
  return null;
}

function restHandler(req, res) {
  const parsed = parseAPIPath(req.path);
  if (!parsed) {
    res.status(404).end();
    return;
  }
  const size = parseInt(req.query.size, 10) || 1;
  const format = req.query.format || 'json';
  const { resource, id } = parsed;

  switch (req.method) {
    case 'GET':
      writePayloadResponse(res, size, format, 'GET', id ? `${resource}/${id}` : resource);
      break;
    case 'POST':
      if (id) res.status(404).end();
      else writePayloadResponse(res, size, format, 'POST', resource);
      break;
    case 'PUT':
      if (!id) res.status(404).end();
      else writePayloadResponse(res, size, format, 'PUT', `${resource}/${id}`);
      break;
    case 'DELETE':
      if (!id) res.status(404).end();
      else writePayloadResponse(res, size, format, 'DELETE', `${resource}/${id}`);
      break;
    default:
      res.status(405).send('Method Not Allowed');
  }
}

module.exports = { restHandler };
