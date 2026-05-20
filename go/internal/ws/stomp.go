package ws

import (
	"encoding/json"
	"math/rand"
	"net/http"
	"strconv"
	"strings"

	"github.com/SidathWeerasinghe/mock-services/go/internal/payload"
	"github.com/gorilla/websocket"
)

const stompNull = "\x00"

func SockJSInfo(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(map[string]interface{}{
		"websocket":     true,
		"cookie_needed": false,
		"origins":       []string{"*:*"},
		"entropy":       rand.Int63n(9_000_000_000) + 1_000_000_000,
	})
}

func StompWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := Upgrader.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	defer conn.Close()

	subs := make(map[string]string)
	_ = conn.WriteMessage(websocket.TextMessage, []byte("o"))

	for {
		_, raw, err := conn.ReadMessage()
		if err != nil {
			break
		}
		var messages []string
		if err := json.Unmarshal(raw, &messages); err != nil {
			continue
		}
		for _, frame := range messages {
			handleStompFrame(conn, frame, subs)
		}
	}
}

func handleStompFrame(conn *websocket.Conn, frame string, subs map[string]string) {
	cmd, headers, body := parseStompFrame(frame)
	switch cmd {
	case "CONNECT":
		sockJSSend(conn, buildStompFrame("CONNECTED", map[string]string{
			"version":    "1.2",
			"heart-beat": "0,0",
		}, ""))
	case "SUBSCRIBE":
		subID := headers["id"]
		if subID == "" {
			subID = "sub-0"
		}
		subs[subID] = headers["destination"]
		if receipt := headers["receipt"]; receipt != "" {
			sockJSSend(conn, buildStompFrame("RECEIPT", map[string]string{"receipt-id": receipt}, ""))
		}
	case "SEND":
		dest := headers["destination"]
		if strings.HasSuffix(dest, "/mock") || dest == "/app/mock" {
			var req struct {
				Size     interface{} `json:"size"`
				Format   string      `json:"format"`
				Method   string      `json:"method"`
				Resource string      `json:"resource"`
			}
			if body != "" {
				_ = json.Unmarshal([]byte(body), &req)
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
				b, _ := json.Marshal(map[string]interface{}{
					"error": err.Error(), "validSizes": payload.ValidSizesKB,
				})
				out = string(b)
			}
			for subID, subDest := range subs {
				if strings.Contains(subDest, "/topic/response") || strings.HasSuffix(subDest, "/response") {
					sockJSSend(conn, buildStompFrame("MESSAGE", map[string]string{
						"subscription": subID,
						"message-id":   "msg-" + strconv.Itoa(rand.Intn(999999)),
						"destination":  subDest,
					}, out))
				}
			}
		}
	}
}

func parseStompFrame(data string) (command string, headers map[string]string, body string) {
	data = strings.TrimSuffix(data, stompNull)
	parts := strings.SplitN(data, "\n\n", 2)
	head := parts[0]
	if len(parts) > 1 {
		body = parts[1]
	}
	lines := strings.Split(head, "\n")
	command = strings.TrimSpace(lines[0])
	headers = make(map[string]string)
	for _, line := range lines[1:] {
		if i := strings.Index(line, ":"); i > 0 {
			headers[strings.TrimSpace(line[:i])] = strings.TrimSpace(line[i+1:])
		}
	}
	return command, headers, body
}

func buildStompFrame(command string, headers map[string]string, body string) string {
	var b strings.Builder
	b.WriteString(command)
	for k, v := range headers {
		b.WriteString("\n")
		b.WriteString(k)
		b.WriteString(":")
		b.WriteString(v)
	}
	b.WriteString("\n\n")
	if body != "" {
		b.WriteString(body)
	}
	b.WriteString(stompNull)
	return b.String()
}

func sockJSSend(conn *websocket.Conn, message string) {
	frame := "a" + string(mustJSON([]string{message}))
	_ = conn.WriteMessage(websocket.TextMessage, []byte(frame))
}

func mustJSON(v interface{}) []byte {
	b, _ := json.Marshal(v)
	return b
}
