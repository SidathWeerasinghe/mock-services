package handler

import (
	"encoding/json"
	"io"
	"net/http"

	"github.com/SidathWeerasinghe/mock-services/go/internal/graphql"
)

const graphiqlHTML = `<!DOCTYPE html>
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
</html>`

func GraphiQL(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	_, _ = w.Write([]byte(graphiqlHTML))
}

func GraphQL(w http.ResponseWriter, r *http.Request) {
	var req graphql.Request
	if r.Method == http.MethodGet {
		req.Query = r.URL.Query().Get("query")
		vars := r.URL.Query().Get("variables")
		if vars != "" {
			_ = json.Unmarshal([]byte(vars), &req.Variables)
		}
	} else {
		body, err := io.ReadAll(r.Body)
		if err != nil {
			writeJSONError(w, http.StatusBadRequest, "Bad Request", err.Error(), "")
			return
		}
		if len(body) > 0 {
			if err := json.Unmarshal(body, &req); err != nil {
				writeJSONError(w, http.StatusBadRequest, "Bad Request", err.Error(), "")
				return
			}
		}
	}

	data, errs := graphql.Execute(req.Query, req.Variables)
	out := map[string]interface{}{}
	if data != nil {
		out["data"] = data
	}
	if len(errs) > 0 {
		out["errors"] = errs
	}
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(out)
}
