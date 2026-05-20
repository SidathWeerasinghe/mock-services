'use strict';

const crypto = require('crypto');
const { generate, VALID_SIZES_KB } = require('../payload/generator');

function makeMockResponse(method, resource, sizeKb, format) {
  const payload = generate(sizeKb, format, method, resource);
  return {
    requestId: crypto.randomUUID(),
    method: method.toUpperCase(),
    resource,
    sizeKb,
    format: (format || 'json').toLowerCase(),
    byteLength: Buffer.byteLength(payload, 'utf8'),
    timestamp: new Date().toISOString(),
    payload,
  };
}

function parseInlineVars(query, variables) {
  if (variables && Object.keys(variables).length > 0) return variables;
  const out = {};
  let m = query.match(/resource\s*:\s*["']([^"']+)["']/i);
  if (m) out.resource = m[1];
  m = query.match(/size\s*:\s*(\d+)/i);
  if (m) out.size = parseInt(m[1], 10);
  m = query.match(/format\s*:\s*["']([^"']+)["']/i);
  if (m) out.format = m[1];
  m = query.match(/method\s*:\s*["']([^"']+)["']/i);
  if (m) out.method = m[1];
  m = query.match(/\bid\s*:\s*["']([^"']+)["']/i);
  if (m) out.id = m[1];
  return out;
}

function strVar(vars, key, def) {
  return vars[key] != null ? String(vars[key]) : def;
}

function intVar(vars, key, def) {
  const v = vars[key];
  if (v == null) return def;
  return parseInt(v, 10) || def;
}

function executeMutation(q, variables) {
  const resource = strVar(variables, 'resource', 'items');
  const size = intVar(variables, 'size', 1);
  const format = strVar(variables, 'format', 'json');

  try {
    if (q.includes('create')) {
      return {
        data: {
          create: {
            success: true,
            operation: 'CREATE',
            affectedId: crypto.randomUUID(),
            response: makeMockResponse('POST', resource, size, format),
          },
        },
      };
    }
    if (q.includes('update')) {
      const id = strVar(variables, 'id', '0');
      return {
        data: {
          update: {
            success: true,
            operation: 'UPDATE',
            affectedId: id,
            response: makeMockResponse('PUT', `${resource}/${id}`, size, format),
          },
        },
      };
    }
    if (q.includes('delete')) {
      const id = strVar(variables, 'id', '0');
      return {
        data: {
          delete: {
            success: true,
            operation: 'DELETE',
            affectedId: id,
            response: makeMockResponse('DELETE', `${resource}/${id}`, size, format),
          },
        },
      };
    }
  } catch (err) {
    return { errors: [{ message: err.message }] };
  }
  return { errors: [{ message: 'Unsupported mutation' }] };
}

function execute(query, variables = {}) {
  const q = (query || '').toLowerCase().trim();
  variables = parseInlineVars(query, variables);

  try {
    if (q.includes('mutation')) return executeMutation(q, variables);
    if (q.includes('health')) {
      return {
        data: {
          health: {
            status: 'UP',
            server: 'MockAPIServer/1.0',
            timestamp: new Date().toISOString(),
          },
        },
      };
    }
    if (q.includes('info')) {
      return {
        data: {
          info: {
            server: 'MockAPIServer/1.0',
            validSizes: VALID_SIZES_KB,
            formats: ['json', 'xml', 'text', 'html'],
            methods: ['GET', 'POST', 'PUT', 'DELETE'],
            httpEndpoint: 'http://localhost:8080/graphql',
            graphiqlUrl: 'http://localhost:8080/graphiql',
            wsSubscriptionUrl: 'ws://localhost:8080/graphql-ws',
            wssSubscriptionUrl: 'wss://localhost:8443/graphql-ws',
          },
        },
      };
    }
    if (q.includes('mock')) {
      const resource = strVar(variables, 'resource', 'items');
      const size = intVar(variables, 'size', 1);
      const format = strVar(variables, 'format', 'json');
      const method = strVar(variables, 'method', 'GET');
      try {
        return { data: { mock: makeMockResponse(method, resource, size, format) } };
      } catch (err) {
        return { errors: [{ message: err.message }] };
      }
    }
    return { errors: [{ message: 'Unsupported GraphQL operation' }] };
  } catch (err) {
    return { errors: [{ message: err.message }] };
  }
}

function makeMockStreamResponse(method, resource, sizeKb, format) {
  const r = makeMockResponse(method, resource, sizeKb, format);
  return {
    requestId: r.requestId,
    method: r.method,
    resource: r.resource,
    sizeKb: r.sizeKb,
    format: r.format,
    byteLength: r.byteLength,
    timestamp: r.timestamp,
    payload: r.payload,
  };
}

module.exports = { execute, makeMockStreamResponse, VALID_SIZES_KB, parseInlineVars };
