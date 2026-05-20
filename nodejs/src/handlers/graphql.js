'use strict';

const { execute } = require('../graphql/executor');

const GRAPHIQL_HTML = `<!DOCTYPE html>
<html>
<head>
  <title>GraphiQL — Mock API Server</title>
  <link href="https://unpkg.com/graphiql@3/graphiql.min.css" rel="stylesheet" />
</head>
<body style="margin:0;overflow:hidden;">
  <div id="graphiql" style="height:100vh;"></div>
  <script crossorigin src="https://unpkg.com/react@18/umd/react.production.min.js"></script>
  <script crossorigin src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"></script>
  <script crossorigin src="https://unpkg.com/graphiql@3/graphiql.min.js"></script>
  <script>
    const fetcher = GraphiQL.createFetcher({ url: '/graphql' });
    ReactDOM.render(
      React.createElement(GraphiQL, { fetcher }),
      document.getElementById('graphiql'),
    );
  </script>
</body>
</html>`;

function graphiql(req, res) {
  res.type('html').send(GRAPHIQL_HTML);
}

function graphqlHttp(req, res) {
  let query = '';
  let variables = {};

  if (req.method === 'GET') {
    query = req.query.query || '';
    if (req.query.variables) {
      try {
        variables = JSON.parse(req.query.variables);
      } catch {
        variables = {};
      }
    }
  } else {
    query = req.body?.query || '';
    variables = req.body?.variables || {};
    if (typeof variables === 'string') {
      try {
        variables = JSON.parse(variables);
      } catch {
        variables = {};
      }
    }
  }

  const result = execute(query, variables);
  res.json(result);
}

module.exports = { graphiql, graphqlHttp };
