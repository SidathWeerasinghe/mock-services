'use strict';

const { WebSocketServer } = require('ws');
const { generate, VALID_SIZES_KB } = require('../payload/generator');

const STOMP_NULL = '\x00';

function parseStompFrame(data) {
  data = data.replace(/\x00$/, '');
  const parts = data.split('\n\n');
  const head = parts[0] || '';
  const body = parts[1] || '';
  const lines = head.split('\n');
  const command = (lines[0] || '').trim();
  const headers = {};
  for (let i = 1; i < lines.length; i++) {
    const idx = lines[i].indexOf(':');
    if (idx > 0) headers[lines[i].slice(0, idx).trim()] = lines[i].slice(idx + 1).trim();
  }
  return { command, headers, body };
}

function buildStompFrame(command, headers = {}, body = '') {
  const lines = [command];
  for (const [k, v] of Object.entries(headers)) lines.push(`${k}:${v}`);
  lines.push('', body);
  return lines.join('\n') + STOMP_NULL;
}

function sockjsSend(ws, message) {
  ws.send('a' + JSON.stringify([message]));
}

function handleStompFrame(ws, frame, subscriptions) {
  const { command, headers, body } = parseStompFrame(frame);

  switch (command) {
    case 'CONNECT':
      sockjsSend(ws, buildStompFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0' }));
      break;
    case 'SUBSCRIBE': {
      const subId = headers.id || 'sub-0';
      subscriptions[subId] = headers.destination;
      if (headers.receipt) {
        sockjsSend(ws, buildStompFrame('RECEIPT', { 'receipt-id': headers.receipt }));
      }
      break;
    }
    case 'SEND': {
      const dest = headers.destination || '';
      if (dest.endsWith('/mock') || dest === '/app/mock') {
        let req = {};
        if (body) {
          try {
            req = JSON.parse(body);
          } catch {
            req = {};
          }
        }
        const size = parseInt(req.size, 10) || 1;
        const format = req.format || 'json';
        const method = req.method || 'GET';
        const resource = req.resource || 'items';
        let out;
        try {
          out = generate(size, format, method, resource);
        } catch (err) {
          out = JSON.stringify({ error: err.message, validSizes: VALID_SIZES_KB });
        }
        for (const [subId, subDest] of Object.entries(subscriptions)) {
          if (subDest.includes('/topic/response') || subDest.endsWith('/response')) {
            sockjsSend(
              ws,
              buildStompFrame('MESSAGE', {
                subscription: subId,
                'message-id': `msg-${Math.floor(Math.random() * 999999)}`,
                destination: subDest,
              }, out)
            );
          }
        }
      }
      break;
    }
    default:
      break;
  }
}

function attachStompWs(server) {
  const wss = new WebSocketServer({ noServer: true });

  server.on('upgrade', (request, socket, head) => {
    const url = new URL(request.url, 'http://localhost');
    if (!url.pathname.endsWith('/websocket')) return;

    wss.handleUpgrade(request, socket, head, (ws) => {
      wss.emit('connection', ws, request);
    });
  });

  wss.on('connection', (ws) => {
    const subscriptions = {};
    ws.send('o');

    ws.on('message', (raw) => {
      let messages;
      try {
        messages = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (!Array.isArray(messages)) messages = [messages];
      for (const msg of messages) {
        if (typeof msg === 'string') handleStompFrame(ws, msg, subscriptions);
      }
    });
  });

  return wss;
}

function sockjsInfo(req, res) {
  res.json({
    websocket: true,
    cookie_needed: false,
    origins: ['*:*'],
    entropy: Math.floor(Math.random() * 9_000_000_000) + 1_000_000_000,
  });
}

module.exports = { attachStompWs, sockjsInfo };
