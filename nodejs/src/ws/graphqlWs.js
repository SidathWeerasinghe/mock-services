'use strict';

const { WebSocketServer } = require('ws');
const { makeMockStreamResponse, VALID_SIZES_KB, parseInlineVars } = require('../graphql/executor');

const MIN_INTERVAL_MS = 500;

function attachGraphqlWs(server, path = '/graphql-ws') {
  const wss = new WebSocketServer({ server, path });

  wss.on('connection', (ws) => {
    const subs = new Map();
    const send = (obj) => ws.send(JSON.stringify(obj));

    ws.on('message', (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        send({ type: 'error', payload: [{ message: 'Invalid JSON frame' }] });
        return;
      }

      const type = msg.type;
      const id = String(msg.id || '');

      if (type === 'connection_init') {
        send({ type: 'connection_ack' });
        return;
      }

      if (type === 'subscribe') {
        const payload = msg.payload || {};
        let variables = payload.variables || {};
        const query = (payload.query || '').toLowerCase();
        if (!query.replace(/_/g, '').includes('mockstream')) {
          send({ id, type: 'error', payload: [{ message: 'Only mockStream subscription is supported' }] });
          return;
        }
        variables = parseInlineVars(payload.query || '', variables);

        if (subs.has(id)) {
          clearInterval(subs.get(id).timer);
          subs.delete(id);
        }

        const resource = variables.resource || 'events';
        const size = parseInt(variables.size, 10) || 1;
        const format = variables.format || 'json';
        const method = variables.method || 'GET';
        let intervalMs = parseInt(variables.intervalMs, 10) || 1000;
        if (intervalMs < MIN_INTERVAL_MS) intervalMs = MIN_INTERVAL_MS;

        const emit = () => {
          try {
            const data = makeMockStreamResponse(method, resource, size, format);
            send({ id, type: 'next', payload: { data: { mockStream: data } } });
          } catch (err) {
            send({
              id,
              type: 'error',
              payload: [{ message: err.message, validSizes: VALID_SIZES_KB }],
            });
            clearInterval(subs.get(id)?.timer);
            subs.delete(id);
          }
        };

        emit();
        const timer = setInterval(emit, intervalMs);
        subs.set(id, { timer });
        return;
      }

      if (type === 'complete') {
        if (subs.has(id)) {
          clearInterval(subs.get(id).timer);
          subs.delete(id);
        }
        send({ id, type: 'complete' });
        return;
      }

      if (type === 'ping') {
        send({ type: 'pong' });
      }
    });

    ws.on('close', () => {
      for (const { timer } of subs.values()) clearInterval(timer);
      subs.clear();
    });
  });

  return wss;
}

module.exports = { attachGraphqlWs };
