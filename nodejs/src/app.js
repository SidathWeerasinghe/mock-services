'use strict';

const path = require('path');
const express = require('express');
const cors = require('cors');

const { restHandler } = require('./handlers/rest');
const { health, info } = require('./handlers/info');
const { tlsInfo } = require('./handlers/tls');
const { graphiql, graphqlHttp } = require('./handlers/graphql');
const { sockjsInfo } = require('./ws/stomp');

function createApp(staticDir) {
  const app = express();
  app.use(cors());
  app.use(express.json());

  const api = express.Router();
  api.all(/.*/, restHandler);
  app.use('/api', api);
  app.get('/health', health);
  app.get('/info', info);
  app.get('/tls-info', tlsInfo);
  app.get('/graphql', graphqlHttp);
  app.post('/graphql', graphqlHttp);
  app.get('/graphiql', graphiql);
  app.get('/ws/info', sockjsInfo);

  const indexPath = path.join(staticDir, 'index.html');
  app.get('/', (req, res) => res.sendFile(indexPath));
  app.get('/index.html', (req, res) => res.sendFile(indexPath));

  return app;
}

module.exports = { createApp };
