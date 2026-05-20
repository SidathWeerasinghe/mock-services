// Mock API Server (Go) — same endpoints and logic as the Java and Python implementations.
//
//   REST:     /api/{resource}?size={kb}&format={json|xml|text|html}
//   STOMP:    /ws (SockJS)
//   Raw WS:   /raw-ws
//   GraphQL:  POST /graphql, GET /graphiql
//   GraphQL:  /graphql-ws (subscriptions)
//   TLS info: /tls-info
package main

import (
	"log"
	"os"
	"path/filepath"

	"github.com/SidathWeerasinghe/mock-services/go/internal/server"
)

func main() {
	cfg := server.DefaultConfig()
	// Resolve static dir relative to executable / module when run from go/
	if _, err := os.Stat(cfg.StaticDir); os.IsNotExist(err) {
		exe, _ := os.Executable()
		alt := filepath.Join(filepath.Dir(exe), "static")
		if _, err2 := os.Stat(alt); err2 == nil {
			cfg.StaticDir = alt
		}
	}
	if wd, err := os.Getwd(); err == nil {
		for _, p := range []string{
			filepath.Join(wd, "static"),
			filepath.Join(wd, "go", "static"),
		} {
			if _, err := os.Stat(p); err == nil {
				cfg.StaticDir = p
				break
			}
		}
	}
	log.Println("Mock API Server (Go) initialized")
	if err := server.Run(cfg); err != nil {
		log.Fatal(err)
	}
}
