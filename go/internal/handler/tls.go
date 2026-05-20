package handler

import (
	"crypto/tls"
	"encoding/json"
	"net/http"
	"strconv"
	"time"
)

func resolveProtocolByPort(port int) string {
	switch port {
	case 8442:
		return "TLSv1.2"
	case 8443:
		return "TLSv1.3"
	case 8444:
		return "TLSv1.2 or TLSv1.3"
	default:
		return "unknown"
	}
}

func TlsInfo(w http.ResponseWriter, r *http.Request) {
	port, _ := strconv.Atoi(r.URL.Port())
	if port == 0 {
		if r.TLS != nil {
			port = 443
		} else {
			port = 8080
		}
	}

	info := map[string]interface{}{
		"timestamp":  time.Now().UTC().Format(time.RFC3339Nano),
		"scheme":     "https",
		"serverPort": port,
		"remoteAddr": r.RemoteAddr,
	}

	if r.TLS != nil {
		info["tlsEnabled"] = true
		info["negotiatedProtocol"] = tlsVersionName(r.TLS.Version)
		info["cipherSuite"] = tls.CipherSuiteName(r.TLS.CipherSuite)
	} else if r.URL.Scheme == "https" || r.Header.Get("X-Forwarded-Proto") == "https" {
		info["tlsEnabled"] = true
		info["negotiatedProtocol"] = resolveProtocolByPort(port)
		info["note"] = "SSLSession attribute unavailable; port-based protocol inferred"
	} else {
		info["scheme"] = "http"
		info["tlsEnabled"] = false
		info["negotiatedProtocol"] = "none (plain HTTP)"
		info["note"] = "Connect via https:// to test TLS"
	}

	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(info)
}

func tlsVersionName(v uint16) string {
	switch v {
	case tls.VersionTLS13:
		return "TLSv1.3"
	case tls.VersionTLS12:
		return "TLSv1.2"
	default:
		return "unknown"
	}
}
