package graphql

import (
	"crypto/rand"
	"encoding/json"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/SidathWeerasinghe/mock-services/go/internal/payload"
)

type Request struct {
	Query     string                 `json:"query"`
	Variables map[string]interface{} `json:"variables"`
}

type MockResponse struct {
	RequestID  string `json:"requestId"`
	Method     string `json:"method"`
	Resource   string `json:"resource"`
	SizeKb     int    `json:"sizeKb"`
	Format     string `json:"format"`
	ByteLength int    `json:"byteLength"`
	Timestamp  string `json:"timestamp"`
	Payload    string `json:"payload"`
}

type HealthResponse struct {
	Status    string `json:"status"`
	Server    string `json:"server"`
	Timestamp string `json:"timestamp"`
}

type ServerInfo struct {
	Server             string   `json:"server"`
	ValidSizes         []int    `json:"validSizes"`
	Formats            []string `json:"formats"`
	Methods            []string `json:"methods"`
	HTTPEndpoint       string   `json:"httpEndpoint"`
	GraphiqlURL        string   `json:"graphiqlUrl"`
	WsSubscriptionURL  string   `json:"wsSubscriptionUrl"`
	WssSubscriptionURL string   `json:"wssSubscriptionUrl"`
}

type MutationResult struct {
	Success    bool         `json:"success"`
	Operation  string       `json:"operation"`
	AffectedID string       `json:"affectedId,omitempty"`
	Response   MockResponse `json:"response"`
}

func newUUID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		uint32(b[0])<<24|uint32(b[1])<<16|uint32(b[2])<<8|uint32(b[3]),
		uint16(b[4])<<8|uint16(b[5]),
		uint16(b[6])<<8|uint16(b[7]),
		uint16(b[8])<<8|uint16(b[9]),
		b[10], b[11], b[12], b[13], b[14], b[15])
}

func makeMockResponse(method, resource string, sizeKb int, format string) (MockResponse, error) {
	payloadStr, err := payload.Generate(sizeKb, format, method, resource)
	if err != nil {
		return MockResponse{}, err
	}
	return MockResponse{
		RequestID:  newUUID(),
		Method:     strings.ToUpper(method),
		Resource:   resource,
		SizeKb:     sizeKb,
		Format:     strings.ToLower(format),
		ByteLength: len([]byte(payloadStr)),
		Timestamp:  time.Now().UTC().Format(time.RFC3339Nano),
		Payload:    payloadStr,
	}, nil
}

// Execute runs a GraphQL query or mutation.
func Execute(query string, variables map[string]interface{}) (map[string]interface{}, []map[string]string) {
	q := strings.ToLower(strings.TrimSpace(query))
	variables = ParseInlineVars(query, variables)

	switch {
	case strings.Contains(q, "mutation"):
		return executeMutation(q, variables)
	case strings.Contains(q, "health"):
		return map[string]interface{}{
			"health": HealthResponse{
				Status:    "UP",
				Server:    "MockAPIServer/1.0",
				Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
			},
		}, nil
	case strings.Contains(q, "info"):
		return map[string]interface{}{
			"info": ServerInfo{
				Server:             "MockAPIServer/1.0",
				ValidSizes:         payload.ValidSizesKB,
				Formats:            []string{"json", "xml", "text", "html"},
				Methods:            []string{"GET", "POST", "PUT", "DELETE"},
				HTTPEndpoint:       "http://localhost:8080/graphql",
				GraphiqlURL:        "http://localhost:8080/graphiql",
				WsSubscriptionURL:  "ws://localhost:8080/graphql-ws",
				WssSubscriptionURL: "wss://localhost:8443/graphql-ws",
			},
		}, nil
	case strings.Contains(q, "mock"):
		resource := strVar(variables, "resource", "items")
		size := intVar(variables, "size", 1)
		format := strVar(variables, "format", "json")
		method := strVar(variables, "method", "GET")
		resp, err := makeMockResponse(method, resource, size, format)
		if err != nil {
			return nil, []map[string]string{{"message": err.Error()}}
		}
		return map[string]interface{}{"mock": resp}, nil
	default:
		return nil, []map[string]string{{"message": "Unsupported GraphQL operation"}}
	}
}

func executeMutation(q string, variables map[string]interface{}) (map[string]interface{}, []map[string]string) {
	resource := strVar(variables, "resource", "items")
	size := intVar(variables, "size", 1)
	format := strVar(variables, "format", "json")

	switch {
	case strings.Contains(q, "create"):
		resp, err := makeMockResponse("POST", resource, size, format)
		if err != nil {
			return nil, []map[string]string{{"message": err.Error()}}
		}
		return map[string]interface{}{
			"create": MutationResult{Success: true, Operation: "CREATE", AffectedID: newUUID(), Response: resp},
		}, nil
	case strings.Contains(q, "update"):
		id := strVar(variables, "id", "0")
		resp, err := makeMockResponse("PUT", resource+"/"+id, size, format)
		if err != nil {
			return nil, []map[string]string{{"message": err.Error()}}
		}
		return map[string]interface{}{
			"update": MutationResult{Success: true, Operation: "UPDATE", AffectedID: id, Response: resp},
		}, nil
	case strings.Contains(q, "delete"):
		id := strVar(variables, "id", "0")
		resp, err := makeMockResponse("DELETE", resource+"/"+id, size, format)
		if err != nil {
			return nil, []map[string]string{{"message": err.Error()}}
		}
		return map[string]interface{}{
			"delete": MutationResult{Success: true, Operation: "DELETE", AffectedID: id, Response: resp},
		}, nil
	default:
		return nil, []map[string]string{{"message": "Unsupported mutation"}}
	}
}

func strVar(vars map[string]interface{}, key, def string) string {
	if v, ok := vars[key]; ok {
		return fmt.Sprint(v)
	}
	return def
}

func intVar(vars map[string]interface{}, key string, def int) int {
	if v, ok := vars[key]; ok {
		switch t := v.(type) {
		case float64:
			return int(t)
		case int:
			return t
		case json.Number:
			i, _ := t.Int64()
			return int(i)
		}
	}
	return def
}

var (
	resourceRe = regexp.MustCompile(`resource\s*:\s*["']([^"']+)["']`)
	sizeRe     = regexp.MustCompile(`size\s*:\s*(\d+)`)
	formatRe   = regexp.MustCompile(`format\s*:\s*["']([^"']+)["']`)
	methodRe   = regexp.MustCompile(`method\s*:\s*["']([^"']+)["']`)
	idRe       = regexp.MustCompile(`\bid\s*:\s*["']([^"']+)["']`)
)

// ParseInlineVars extracts arguments from the query when variables are not supplied.
func ParseInlineVars(query string, variables map[string]interface{}) map[string]interface{} {
	if len(variables) > 0 {
		return variables
	}
	out := map[string]interface{}{}
	if m := resourceRe.FindStringSubmatch(query); len(m) > 1 {
		out["resource"] = m[1]
	}
	if m := sizeRe.FindStringSubmatch(query); len(m) > 1 {
		if n, err := strconv.Atoi(m[1]); err == nil {
			out["size"] = n
		}
	}
	if m := formatRe.FindStringSubmatch(query); len(m) > 1 {
		out["format"] = m[1]
	}
	if m := methodRe.FindStringSubmatch(query); len(m) > 1 {
		out["method"] = m[1]
	}
	if m := idRe.FindStringSubmatch(query); len(m) > 1 {
		out["id"] = m[1]
	}
	return out
}

// MakeMockStreamResponse builds a mockStream payload for WebSocket subscriptions.
func MakeMockStreamResponse(method, resource string, sizeKb int, format string) (map[string]interface{}, error) {
	resp, err := makeMockResponse(method, resource, sizeKb, format)
	if err != nil {
		return nil, err
	}
	return map[string]interface{}{
		"requestId":  resp.RequestID,
		"method":     resp.Method,
		"resource":   resp.Resource,
		"sizeKb":     resp.SizeKb,
		"format":     resp.Format,
		"byteLength": resp.ByteLength,
		"timestamp":  resp.Timestamp,
		"payload":    resp.Payload,
	}, nil
}
