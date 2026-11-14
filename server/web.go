package main

import (
	"embed"
	"net/http"
	"strings"
)

//go:embed web
var webFiles embed.FS

func (s *Server) serveWeb(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	path := r.URL.Path
	switch path {
	case "/", "/index.html":
		path = "web/index.html"
	case "/app.js":
		path = "web/app.js"
	case "/style.css":
		path = "web/style.css"
	case "/vendor/vue.min.js":
		path = "web/vendor/vue.min.js"
	case "/admin", "/admin.html":
		path = "web/admin.html"
	case "/admin.js":
		path = "web/admin.js"
	default:
		writeAPIError(w, http.StatusNotFound, "not_found", "page not found")
		return
	}
	data, err := webFiles.ReadFile(path)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	switch {
	case strings.HasSuffix(path, ".html"):
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
	case strings.HasSuffix(path, ".js"):
		w.Header().Set("Content-Type", "text/javascript; charset=utf-8")
	case strings.HasSuffix(path, ".css"):
		w.Header().Set("Content-Type", "text/css; charset=utf-8")
	}
	_, _ = w.Write(data)
}
