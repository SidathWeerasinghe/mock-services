'use strict';

const { WebSocketServer } = require('ws');
const { generate, VALID_SIZES_KB } = require('../payload/generator');

function attachRawWs(server, path = '/raw-ws') {
  const wss = new WebSocketServer({ server, path });

  wss.on('connection', (ws, req) => {
    ws.send(JSON.stringify({
      event: 'connected',
      sessionId: 'node-ws',
      remoteAddr: req.socket.remoteAddress,
      validSizes: VALID_SIZES_KB,
      formats: ['json', 'xml'],
      methods: ['GET', 'POST', 'PUT', 'DELETE'],
      usage: 'Send JSON: {"size":5,"format":"json","method":"GET","resource":"orders"}',
    }));

    ws.on('message', (data) => {
      try {
        const reqBody = JSON.parse(data.toString());
        const size = parseInt(reqBody.size, 10) || 1;
        const format = reqBody.format || 'json';
        const method = reqBody.method || 'GET';
        const resource = reqBody.resource || 'items';
        const payload = generate(size, format, method, resource);
        ws.send(payload);
      } catch (err) {
        if (err.message && err.message.includes('Invalid size')) {
          ws.send(JSON.stringify({ error: err.message, validSizes: VALID_SIZES_KB }));
        } else {
          ws.send(JSON.stringify({ error: `Internal error: ${err.message}` }));
        }
      }
    });
  });

  return wss;
}

module.exports = { attachRawWs };
