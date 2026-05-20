package handler

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"
)

func writeValidationError(w http.ResponseWriter, err error) {
	writeJSONError(w, http.StatusBadRequest, "Bad Request", err.Error(),
		"Valid sizes (KB): 1,2,3,4,5,6,7,8,9,10,15,20 | formats: json, xml")
}

func writeJSONError(w http.ResponseWriter, status int, title, message, hint string) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	body := map[string]interface{}{
		"status":    status,
		"error":     title,
		"message":   message,
		"timestamp": time.Now().UTC().Format(time.RFC3339Nano),
	}
	if hint != "" {
		body["hint"] = hint
	}
	_ = json.NewEncoder(w).Encode(body)
}

func handlePayloadError(w http.ResponseWriter, err error) bool {
	if err != nil && strings.Contains(err.Error(), "Invalid size") {
		writeValidationError(w, err)
		return true
	}
	return false
}
