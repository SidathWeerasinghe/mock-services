package ws

import (
	"encoding/json"
	"net/http"
	"strconv"

	"github.com/SidathWeerasinghe/mock-services/go/internal/payload"
	"github.com/gorilla/websocket"
)

type rawRequest struct {
	Size     interface{} `json:"size"`
	Format   string      `json:"format"`
	Method   string      `json:"method"`
	Resource string      `json:"resource"`
}

func RawWS(w http.ResponseWriter, r *http.Request) {
	conn, err := Upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	welcome, _ := json.Marshal(map[string]interface{}{
		"event":      "connected",
		"sessionId":  "go-ws",
		"remoteAddr": r.RemoteAddr,
		"validSizes": payload.ValidSizesKB,
		"formats":    []string{"json", "xml"},
		"methods":    []string{"GET", "POST", "PUT", "DELETE"},
		"usage":      `Send JSON: {"size":5,"format":"json","method":"GET","resource":"orders"}`,
	})
	_ = conn.WriteMessage(websocket.TextMessage, welcome)

	for {
		_, msg, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var req rawRequest
		if err := json.Unmarshal(msg, &req); err != nil {
			sendRawError(conn, "Invalid JSON: "+err.Error())
			continue
		}
		size := toInt(req.Size, 1)
		format := req.Format
		if format == "" {
			format = "json"
		}
		method := req.Method
		if method == "" {
			method = "GET"
		}
		resource := req.Resource
		if resource == "" {
			resource = "items"
		}
		out, err := payload.Generate(size, format, method, resource)
		if err != nil {
			errBody, _ := json.Marshal(map[string]interface{}{
				"error":      err.Error(),
				"validSizes": payload.ValidSizesKB,
			})
			_ = conn.WriteMessage(websocket.TextMessage, errBody)
			continue
		}
		_ = conn.WriteMessage(websocket.TextMessage, []byte(out))
	}
}

func sendRawError(conn *websocket.Conn, msg string) {
	b, _ := json.Marshal(map[string]string{"error": msg})
	_ = conn.WriteMessage(websocket.TextMessage, b)
}

func toInt(v interface{}, def int) int {
	switch t := v.(type) {
	case float64:
		return int(t)
	case int:
		return t
	case string:
		if n, err := strconv.Atoi(t); err == nil {
			return n
		}
	}
	return def
}
