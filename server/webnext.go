package main

import (
	"embed"
	"io/fs"
	"net/http"
	"path"
	"strings"
)

//go:embed webnext
var webNextFS embed.FS

func (s *Server) serveWebNext(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodHead {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	relative := strings.TrimPrefix(r.URL.Path, "/v2/")
	relative = path.Clean("/" + relative)
	if strings.Contains(relative, "..") {
		writeAPIError(w, http.StatusNotFound, "not_found", "page not found")
		return
	}
	if relative == "/" {
		relative = "/index.html"
	}
	data, err := fs.ReadFile(webNextFS, "webnext"+relative)
	if err != nil {
		// SPA routes fall back to the shell.
		if !strings.Contains(path.Base(relative), ".") {
			data, err = fs.ReadFile(webNextFS, "webnext/index.html")
		}
	}
	if err != nil {
		writeAPIError(w, http.StatusNotFound, "not_found", "page not found")
		return
	}
	w.Header().Set("Content-Type", contentTypeFor(relative))
	_, _ = w.Write(data)
}

func contentTypeFor(name string) string {
	ext := strings.ToLower(path.Ext(name))
	switch ext {
	case ".html":
		return "text/html; charset=utf-8"
	case ".js":
		return "text/javascript; charset=utf-8"
	case ".css":
		return "text/css; charset=utf-8"
	case ".svg":
		return "image/svg+xml"
	case ".png":
		return "image/png"
	case ".ico":
		return "image/x-icon"
	case ".webmanifest":
		return "application/manifest+json"
	default:
		return "application/octet-stream"
	}
}
