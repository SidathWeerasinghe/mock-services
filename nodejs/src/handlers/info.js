'use strict';

const { VALID_SIZES_KB } = require('../payload/generator');

function health(req, res) {
  res.json({
    status: 'UP',
    server: 'MockAPIServer/1.0',
    timestamp: new Date().toISOString(),
  });
}

function info(req, res) {
  res.json({
    server: 'MockAPIServer/1.0',
    validSizes: VALID_SIZES_KB,
    formats: ['json', 'xml', 'text', 'html'],
    methods: ['GET', 'POST', 'PUT', 'DELETE'],
    endpoints: {
      'REST (HTTP)': 'http://localhost:8080/api/{resource}?size={kb}&format={json|xml|text|html}',
      'REST (HTTPS)': 'https://localhost:8443/api/{resource}?size={kb}&format={json|xml|text|html}',
      'WebSocket (WS)': 'ws://localhost:8080/ws',
      'WebSocket (WSS)': 'wss://localhost:8443/ws',
      'Raw WS': 'ws://localhost:8080/raw-ws',
      'Raw WSS': 'wss://localhost:8443/raw-ws',
      'GraphQL HTTP': 'http://localhost:8080/graphql  (POST — Query & Mutation)',
      'GraphiQL IDE': 'http://localhost:8080/graphiql',
      'GraphQL WS': 'ws://localhost:8080/graphql-ws   (Subscription)',
      'GraphQL WSS': 'wss://localhost:8443/graphql-ws  (Subscription)',
      'Test UI': 'http://localhost:8080/index.html',
    },
    examples: {
      'GET 5KB JSON': 'GET /api/orders?size=5&format=json',
      'GET 10KB XML': 'GET /api/orders?size=10&format=xml',
      'POST 2KB JSON': 'POST /api/users?size=2&format=json',
      'PUT 3KB XML': 'PUT /api/users/42?size=3&format=xml',
      'DELETE 1KB JSON': 'DELETE /api/products/99?size=1&format=json',
      'GraphQL Query': 'POST /graphql  {"query":"{ mock(resource:\\"orders\\", size:5) { sizeKb byteLength } }"}',
      'GraphQL Mutation': 'POST /graphql  {"query":"mutation { create(resource:\\"users\\", size:2) { success affectedId } }"}',
      'GraphQL Subscribe': 'WS  /graphql-ws  subscription { mockStream(resource:\\"events\\", size:3, intervalMs:2000) { timestamp payload } }',
    },
  });
}

module.exports = { health, info };
