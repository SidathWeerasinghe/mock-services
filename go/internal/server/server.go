package server

import (
	"crypto/tls"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/SidathWeerasinghe/mock-services/go/internal/cert"
	"github.com/SidathWeerasinghe/mock-services/go/internal/handler"
	"github.com/SidathWeerasinghe/mock-services/go/internal/ws"
)

// Config holds listener ports and TLS settings.
type Config struct {
	HTTPPort    int
	TLS12Port   int
	TLS13Port   int
	TLS1213Port int
	SSLEnabled  bool
	CertDir     string
	StaticDir   string
}

// DefaultConfig reads configuration from environment variables.
func DefaultConfig() Config {
	cfg := Config{
		HTTPPort:    envInt("MOCK_HTTP_PORT", 8080),
		TLS12Port:   envInt("MOCK_TLS12_PORT", 8442),
		TLS13Port:   envInt("MOCK_TLS13_PORT", 8443),
		TLS1213Port: envInt("MOCK_TLS12_13_PORT", 8444),
		SSLEnabled:  envBool("MOCK_SSL_ENABLED", true),
		CertDir:     envStr("MOCK_CERT_DIR", "certs"),
		StaticDir:   envStr("MOCK_STATIC_DIR", "static"),
	}
	if v := os.Getenv("PORT"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			cfg.HTTPPort = n
		}
	}
	return cfg
}

func envStr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	if v := os.Getenv(key); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			return n
		}
	}
	return def
}

func envBool(key string, def bool) bool {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	return v == "1" || v == "true" || v == "yes"
}

// NewMux builds the HTTP handler with all routes.
func NewMux(staticDir string) http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("/api/", handler.REST)
	mux.HandleFunc("/health", handler.Health)
	mux.HandleFunc("/info", handler.Info)
	mux.HandleFunc("/tls-info", handler.TlsInfo)
	mux.HandleFunc("/graphql", handler.GraphQL)
	mux.HandleFunc("/graphiql", handler.GraphiQL)
	mux.HandleFunc("/raw-ws", ws.RawWS)
	mux.HandleFunc("/graphql-ws", ws.GraphQLWS)
	mux.HandleFunc("/ws/info", ws.SockJSInfo)
	mux.HandleFunc("/ws/", stompDispatcher)

	if staticDir == "" {
		staticDir = "static"
	}
	indexPath := filepath.Join(staticDir, "index.html")
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path == "/" || r.URL.Path == "/index.html" {
			http.ServeFile(w, r, indexPath)
			return
		}
		http.NotFound(w, r)
	})

	return handler.CORS(mux)
}

func stompDispatcher(w http.ResponseWriter, r *http.Request) {
	if strings.HasSuffix(r.URL.Path, "/websocket") {
		ws.StompWebSocket(w, r)
		return
	}
	http.NotFound(w, r)
}

// Run starts HTTP and optional TLS listeners (blocks).
func Run(cfg Config) error {
	h := NewMux(cfg.StaticDir)

	if !cfg.SSLEnabled {
		log.Printf("Mock API Server (Go) — HTTP only on port %d", cfg.HTTPPort)
		return http.ListenAndServe(fmt.Sprintf(":%d", cfg.HTTPPort), h)
	}

	certFile, keyFile, err := cert.Ensure(cfg.CertDir)
	if err != nil {
		log.Printf("SSL setup failed (%v) — falling back to HTTP on %d", err, cfg.HTTPPort)
		return http.ListenAndServe(fmt.Sprintf(":%d", cfg.HTTPPort), h)
	}

	type listener struct {
		port  int
		tls   *tls.Config
		label string
	}

	listeners := []listener{
		{cfg.HTTPPort, nil, "HTTP"},
		{cfg.TLS12Port, tlsConfig(certFile, keyFile, tls.VersionTLS12, tls.VersionTLS12), "TLS 1.2"},
		{cfg.TLS13Port, tlsConfig(certFile, keyFile, tls.VersionTLS13, tls.VersionTLS13), "TLS 1.3"},
		{cfg.TLS1213Port, tlsConfig(certFile, keyFile, tls.VersionTLS12, tls.VersionTLS13), "TLS 1.2+1.3"},
	}

	errCh := make(chan error, len(listeners))
	for _, l := range listeners {
		l := l
		go func() {
			addr := fmt.Sprintf(":%d", l.port)
			srv := &http.Server{
				Addr:              addr,
				Handler:           h,
				TLSConfig:         l.tls,
				ReadHeaderTimeout: 10 * time.Second,
			}
			var err error
			if l.tls == nil {
				log.Printf("Listening on http://0.0.0.0%s (%s)", addr, l.label)
				err = srv.ListenAndServe()
			} else {
				log.Printf("Listening on https://0.0.0.0%s (%s)", addr, l.label)
				err = srv.ListenAndServeTLS(certFile, keyFile)
			}
			if err != nil && err != http.ErrServerClosed {
				errCh <- err
			}
		}()
	}

	log.Printf("Mock API Server (Go) — ports %d (HTTP), %d (TLS1.2), %d (TLS1.3), %d (TLS1.2+1.3)",
		cfg.HTTPPort, cfg.TLS12Port, cfg.TLS13Port, cfg.TLS1213Port)

	return <-errCh
}

func tlsConfig(certFile, keyFile string, min, max uint16) *tls.Config {
	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		log.Fatalf("load cert: %v", err)
	}
	return &tls.Config{
		Certificates: []tls.Certificate{cert},
		MinVersion:   min,
		MaxVersion:   max,
	}
}
