package handler

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/SidathWeerasinghe/mock-services/go/internal/payload"
)

func mimeForFormat(format string) string {
	switch strings.ToLower(format) {
	case "xml":
		return "application/xml"
	case "text":
		return "text/plain"
	case "html":
		return "text/html"
	default:
		return "application/json"
	}
}

func writePayloadResponse(w http.ResponseWriter, size int, format, method, resource string) {
	payloadStr, err := payload.Generate(size, format, method, resource)
	if handlePayloadError(w, err) {
		return
	}
	w.Header().Set("Content-Type", mimeForFormat(format))
	w.Header().Set("X-Mock-Size-KB", strconv.Itoa(size))
	w.Header().Set("X-Mock-Format", strings.ToLower(format))
	w.Header().Set("X-Mock-Method", method)
	w.Header().Set("X-Mock-Resource", resource)
	w.Header().Set("X-Payload-Bytes", strconv.Itoa(len([]byte(payloadStr))))
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(payloadStr))
}

func parseSizeFormat(r *http.Request) (size int, format string) {
	size = 1
	format = "json"
	if s := r.URL.Query().Get("size"); s != "" {
		if v, err := strconv.Atoi(s); err == nil {
			size = v
		}
	}
	if f := r.URL.Query().Get("format"); f != "" {
		format = f
	}
	return size, format
}

func parseAPIPath(path string) (resource, id string, ok bool) {
	path = strings.TrimPrefix(path, "/api/")
	if path == "" || strings.HasPrefix(path, "/") {
		return "", "", false
	}
	parts := strings.Split(path, "/")
	if len(parts) == 1 {
		return parts[0], "", true
	}
	if len(parts) == 2 {
		return parts[0], parts[1], true
	}
	return "", "", false
}

// REST dispatches /api/* requests by method.
func REST(w http.ResponseWriter, r *http.Request) {
	resource, id, ok := parseAPIPath(r.URL.Path)
	if !ok {
		http.NotFound(w, r)
		return
	}
	size, format := parseSizeFormat(r)
	switch r.Method {
	case http.MethodGet:
		if id != "" {
			writePayloadResponse(w, size, format, "GET", resource+"/"+id)
		} else {
			writePayloadResponse(w, size, format, "GET", resource)
		}
	case http.MethodPost:
		if id == "" {
			writePayloadResponse(w, size, format, "POST", resource)
		} else {
			http.NotFound(w, r)
		}
	case http.MethodPut:
		if id != "" {
			writePayloadResponse(w, size, format, "PUT", resource+"/"+id)
		} else {
			http.NotFound(w, r)
		}
	case http.MethodDelete:
		if id != "" {
			writePayloadResponse(w, size, format, "DELETE", resource+"/"+id)
		} else {
			http.NotFound(w, r)
		}
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}
