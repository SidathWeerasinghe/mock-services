package ws

import (
	"encoding/json"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/SidathWeerasinghe/mock-services/go/internal/graphql"
	payloadgen "github.com/SidathWeerasinghe/mock-services/go/internal/payload"
	"github.com/gorilla/websocket"
)

const minIntervalMs = 500

func GraphQLWS(w http.ResponseWriter, r *http.Request) {
	conn, err := Upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	subs := make(map[string]chan struct{})
	var mu sync.Mutex

	stopAll := func() {
		mu.Lock()
		defer mu.Unlock()
		for id, ch := range subs {
			close(ch)
			delete(subs, id)
		}
	}
	defer stopAll()

	send := func(v interface{}) {
		b, _ := json.Marshal(v)
		_ = conn.WriteMessage(websocket.TextMessage, b)
	}

	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var msg map[string]interface{}
		if err := json.Unmarshal(raw, &msg); err != nil {
			send(map[string]interface{}{"type": "error", "payload": []map[string]string{{"message": "Invalid JSON frame"}}})
			continue
		}
		msgType, _ := msg["type"].(string)
		subID, _ := msg["id"].(string)

		switch msgType {
		case "connection_init":
			send(map[string]string{"type": "connection_ack"})

		case "subscribe":
			subPayload, _ := msg["payload"].(map[string]interface{})
			vars, _ := subPayload["variables"].(map[string]interface{})
			query, _ := subPayload["query"].(string)
			if !strings.Contains(strings.ToLower(strings.ReplaceAll(query, "_", "")), "mockstream") {
				send(map[string]interface{}{
					"id": subID, "type": "error",
					"payload": []map[string]string{{"message": "Only mockStream subscription is supported"}},
				})
				continue
			}
			if vars == nil {
				vars = graphql.ParseInlineVars(query, nil)
			}
			resource := graphqlStrVar(vars, "resource", "events")
			size := graphqlIntVar(vars, "size", 1)
			format := graphqlStrVar(vars, "format", "json")
			method := graphqlStrVar(vars, "method", "GET")
			intervalMs := graphqlIntVar(vars, "intervalMs", 1000)
			if intervalMs < minIntervalMs {
				intervalMs = minIntervalMs
			}

			mu.Lock()
			if ch, ok := subs[subID]; ok {
				close(ch)
			}
			stop := make(chan struct{})
			subs[subID] = stop
			mu.Unlock()

			go func(id string, stopCh chan struct{}) {
				emit := func() bool {
					data, err := graphql.MakeMockStreamResponse(method, resource, size, format)
					if err != nil {
						send(map[string]interface{}{
							"id": id, "type": "error",
							"payload": []map[string]interface{}{{"message": err.Error(), "validSizes": payloadgen.ValidSizesKB}},
						})
						return false
					}
					send(map[string]interface{}{
						"id": id, "type": "next",
						"payload": map[string]interface{}{"data": map[string]interface{}{"mockStream": data}},
					})
					return true
				}
				if !emit() {
					return
				}
				ticker := time.NewTicker(time.Duration(intervalMs) * time.Millisecond)
				defer ticker.Stop()
				for {
					select {
					case <-stopCh:
						return
					case <-ticker.C:
						if !emit() {
							return
						}
					}
				}
			}(subID, stop)

		case "complete":
			mu.Lock()
			if ch, ok := subs[subID]; ok {
				close(ch)
				delete(subs, subID)
			}
			mu.Unlock()
			send(map[string]string{"id": subID, "type": "complete"})

		case "ping":
			send(map[string]string{"type": "pong"})
		}
	}
}

func graphqlStrVar(vars map[string]interface{}, key, def string) string {
	if v, ok := vars[key]; ok {
		switch t := v.(type) {
		case string:
			return t
		default:
			b, _ := json.Marshal(t)
			return strings.Trim(string(b), `"`)
		}
	}
	return def
}

func graphqlIntVar(vars map[string]interface{}, key string, def int) int {
	if v, ok := vars[key]; ok {
		switch t := v.(type) {
		case float64:
			return int(t)
		case int:
			return t
		}
	}
	return def
}
