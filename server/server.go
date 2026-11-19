package main

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/crypto/bcrypt"
)

const (
	defaultTokenTTLHours = 720
	productionBcryptCost = 12
	maxAuthJSONBytes     = 16 << 10
	maxProviderJSONBytes = 2 << 20
	maxConversationBytes = 16 << 20
	maxUploadBytes       = 10 << 20
	maxUploadBodyBytes   = maxUploadBytes + (1 << 20)
)

// Config is the server's internal configuration. Environment parsing lives in
// main.go; tests construct Config directly.
type Config struct {
	DataDir        string
	MasterKey      []byte
	TokenTTL       time.Duration
	BcryptCost     int
	AuthRateLimit  int
	AuthRateWindow time.Duration
	Logger         *log.Logger
	Now            func() time.Time
	AdminToken     string
	DSHGatewayURL  string
}

type Server struct {
	cfg       Config
	db        *sql.DB
	filesDir  string
	aead      *dataCipher
	limiter   *rateLimiter
	events    *eventHub
	relay     *relayHub
	logger    *log.Logger
	dummyHash []byte
	now       func() time.Time
}

func NewServer(cfg Config) (*Server, error) {
	if cfg.BcryptCost == 0 {
		cfg.BcryptCost = productionBcryptCost
	}
	if cfg.BcryptCost < bcrypt.MinCost || cfg.BcryptCost > 31 {
		return nil, fmt.Errorf("bcrypt cost must be between %d and 31", bcrypt.MinCost)
	}
	if cfg.TokenTTL <= 0 {
		cfg.TokenTTL = defaultTokenTTLHours * time.Hour
	}
	if cfg.AuthRateLimit == 0 {
		cfg.AuthRateLimit = 20
	}
	if cfg.AuthRateLimit < 0 {
		return nil, errors.New("auth rate limit must not be negative")
	}
	if cfg.AuthRateWindow <= 0 {
		cfg.AuthRateWindow = time.Minute
	}
	if cfg.Logger == nil {
		cfg.Logger = log.New(os.Stdout, "", log.LstdFlags|log.LUTC)
	}
	if cfg.Now == nil {
		cfg.Now = time.Now
	}

	absDataDir, err := filepath.Abs(cfg.DataDir)
	if err != nil {
		return nil, fmt.Errorf("resolve data directory: %w", err)
	}
	filesDir := filepath.Join(absDataDir, "files")
	if err := os.MkdirAll(filesDir, 0o700); err != nil {
		return nil, fmt.Errorf("create file directory: %w", err)
	}
	_ = os.Chmod(filesDir, 0o700)

	aead, err := newDataCipher(cfg.MasterKey)
	if err != nil {
		return nil, err
	}
	db, err := openStore(absDataDir)
	if err != nil {
		return nil, err
	}

	dummyPassword := make([]byte, 24)
	if _, err := rand.Read(dummyPassword); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("generate dummy password: %w", err)
	}
	dummyHash, err := bcrypt.GenerateFromPassword([]byte(base64.RawURLEncoding.EncodeToString(dummyPassword)), cfg.BcryptCost)
	if err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("initialize password hashing: %w", err)
	}

	s := &Server{
		cfg:       cfg,
		db:        db,
		filesDir:  filesDir,
		aead:      aead,
		limiter:   newRateLimiter(cfg.AuthRateLimit, cfg.AuthRateWindow, cfg.Now),
		events:    newEventHub(),
		relay:     newRelayHub(),
		logger:    cfg.Logger,
		dummyHash: dummyHash,
		now:       cfg.Now,
	}
	s.cleanupExpiredTokens(context.Background())
	return s, nil
}

func (s *Server) Close() error {
	if s.db == nil {
		return nil
	}
	return s.db.Close()
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	start := time.Now()
	rec := &responseRecorder{ResponseWriter: w}
	s.setSecurityHeaders(rec, r)

	defer func() {
		if recovered := recover(); recovered != nil {
			s.logger.Printf("panic while handling request method=%s path=%q: %v", r.Method, r.URL.Path, recovered)
			if !rec.wrote {
				writeAPIError(rec, http.StatusInternalServerError, "internal_error", "internal server error")
			}
		}
		status := rec.status
		if !rec.wrote {
			status = http.StatusOK
		}
		s.logger.Printf("http method=%s path=%q status=%d duration=%s", r.Method, r.URL.Path, status, time.Since(start).Round(time.Microsecond))
	}()

	s.route(rec, r)
}

func (s *Server) route(w http.ResponseWriter, r *http.Request) {
	path := r.URL.Path
	if path == "/v2" {
		http.Redirect(w, r, "/v2/", http.StatusTemporaryRedirect)
		return
	}
	if strings.HasPrefix(path, "/v2/") {
		s.serveWebNext(w, r)
		return
	}
	switch path {
	case "/", "/index.html", "/app.js", "/style.css", "/vendor/vue.min.js",
		"/admin", "/admin.html", "/admin.js",
		"/favicon.ico", "/icon.svg", "/icon-192.png", "/icon-512.png",
		"/apple-touch-icon.png", "/site.webmanifest":
		s.serveWeb(w, r)
		return
	}
	if strings.HasPrefix(path, "/api/admin/") {
		s.handleAdmin(w, r)
		return
	}
	// /api/* is the public prefix. Keep /api/v1/* working for existing
	// Android clients.
	if strings.HasPrefix(path, "/api/") && !strings.HasPrefix(path, "/api/v1/") {
		path = "/api/v1/" + strings.TrimPrefix(path, "/api/")
	}
	switch path {
	case "/healthz":
		if r.Method != http.MethodGet {
			methodNotAllowed(w, http.MethodGet)
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
		return
	case "/api/v1/events":
		s.handleEvents(w, r)
		return
	case "/api/v1/account":
		s.handleAccount(w, r)
		return
	case "/api/v1/account/upgrade-requests":
		s.handleUpgradeRequests(w, r)
		return
	case "/api/v1/relay/jobs":
		s.handleRelayCreate(w, r)
		return
	case "/api/v1/relay/providers":
		s.handleRelayProviders(w, r)
		return
	case "/api/v1/agent/run":
		s.handleAgentRun(w, r)
		return
	case "/api/v1/auth/register":
		if r.Method != http.MethodPost {
			methodNotAllowed(w, http.MethodPost)
			return
		}
		if !s.allowAuthAttempt(w, r) {
			return
		}
		s.handleRegister(w, r)
		return
	case "/api/v1/auth/login":
		if r.Method != http.MethodPost {
			methodNotAllowed(w, http.MethodPost)
			return
		}
		if !s.allowAuthAttempt(w, r) {
			return
		}
		s.handleLogin(w, r)
		return
	case "/api/v1/auth/me":
		if r.Method != http.MethodGet {
			methodNotAllowed(w, http.MethodGet)
			return
		}
		s.handleMe(w, r)
		return
	case "/api/v1/auth/logout":
		if r.Method != http.MethodPost {
			methodNotAllowed(w, http.MethodPost)
			return
		}
		s.handleLogout(w, r)
		return
	case "/api/v1/providers":
		if r.Method != http.MethodGet {
			methodNotAllowed(w, http.MethodGet)
			return
		}
		s.handleListProviders(w, r)
		return
	case "/api/v1/conversations":
		switch r.Method {
		case http.MethodPost:
			s.handleCreateConversation(w, r)
		case http.MethodGet:
			s.handleListConversations(w, r)
		default:
			methodNotAllowed(w, http.MethodGet, http.MethodPost)
		}
		return
	case "/api/v1/files":
		if r.Method != http.MethodPost {
			methodNotAllowed(w, http.MethodPost)
			return
		}
		s.handleUploadFile(w, r)
		return
	}

	if path == "/api/v1/provider-models" {
		s.handleProviderModelsFetch(w, r)
		return
	}
	if rest, ok := strings.CutPrefix(path, "/api/v1/providers/"); ok && strings.HasSuffix(rest, "/models") {
		id := strings.TrimSuffix(rest, "/models")
		if id != "" && !strings.Contains(id, "/") {
			s.handleProviderModels(w, r, id)
			return
		}
	}
	if id, ok := pathValue(path, "/api/v1/providers/"); ok {
		s.handleProviderItem(w, r, id)
		return
	}
	if rest, ok := strings.CutPrefix(path, "/api/v1/conversations/"); ok && strings.HasSuffix(rest, "/title") {
		id := strings.TrimSuffix(rest, "/title")
		if id != "" && !strings.Contains(id, "/") {
			s.handleConversationTitle(w, r, id)
			return
		}
	}
	if id, ok := pathValue(path, "/api/v1/conversations/"); ok {
		s.handleConversationItem(w, r, id)
		return
	}
	if id, ok := pathValue(path, "/api/v1/files/"); ok {
		s.handleDownloadFile(w, r, id)
		return
	}
	if rest, ok := strings.CutPrefix(path, "/api/v1/relay/jobs/"); ok {
		s.handleRelayJobItem(w, r, rest)
		return
	}
	writeAPIError(w, http.StatusNotFound, "not_found", "endpoint not found")
}

func pathValue(path, prefix string) (string, bool) {
	if !strings.HasPrefix(path, prefix) {
		return "", false
	}
	value := strings.TrimPrefix(path, prefix)
	if value == "" || strings.Contains(value, "/") {
		return "", false
	}
	return value, true
}

func (s *Server) allowAuthAttempt(w http.ResponseWriter, r *http.Request) bool {
	ok, retryAfter := s.limiter.Allow(s.clientIP(r))
	if ok {
		return true
	}
	seconds := int(math.Ceil(retryAfter.Seconds()))
	if seconds < 1 {
		seconds = 1
	}
	w.Header().Set("Retry-After", fmt.Sprintf("%d", seconds))
	writeAPIError(w, http.StatusTooManyRequests, "rate_limited", "too many authentication attempts; try again later")
	return false
}

func (s *Server) clientIP(r *http.Request) string {
	// Caddy overwrites X-Real-IP and the app is not published directly.
	if candidate := strings.TrimSpace(r.Header.Get("X-Real-IP")); candidate != "" {
		if ip := net.ParseIP(candidate); ip != nil {
			return ip.String()
		}
	}
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err == nil && host != "" {
		return host
	}
	if r.RemoteAddr != "" {
		return r.RemoteAddr
	}
	return "unknown"
}

func (s *Server) setSecurityHeaders(w http.ResponseWriter, r *http.Request) {
	h := w.Header()
	h.Set("X-Content-Type-Options", "nosniff")
	h.Set("X-Frame-Options", "DENY")
	h.Set("Referrer-Policy", "no-referrer")
	h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
	h.Set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; font-src 'self'; frame-ancestors 'none'; base-uri 'none'")
	h.Set("Cache-Control", "no-store")
	if r.TLS != nil || strings.EqualFold(r.Header.Get("X-Forwarded-Proto"), "https") {
		h.Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
	}
}

func (s *Server) internalError(w http.ResponseWriter, r *http.Request, err error) {
	s.logger.Printf("internal error method=%s path=%q: %v", r.Method, r.URL.Path, err)
	writeAPIError(w, http.StatusInternalServerError, "internal_error", "internal server error")
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	payload, err := json.Marshal(value)
	if err != nil {
		http.Error(w, `{"error":{"code":"internal_error","message":"internal server error"}}`, http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_, _ = w.Write(append(payload, '\n'))
}

func writeAPIError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, ErrorResponse{Error: ErrorBody{Code: code, Message: message}})
}

func writeNoContent(w http.ResponseWriter) {
	w.WriteHeader(http.StatusNoContent)
}

func methodNotAllowed(w http.ResponseWriter, methods ...string) {
	w.Header().Set("Allow", strings.Join(methods, ", "))
	writeAPIError(w, http.StatusMethodNotAllowed, "method_not_allowed", "method not allowed")
}

func decodeJSONBody(w http.ResponseWriter, r *http.Request, dst any, maxBytes int64) *apiError {
	if r.ContentLength > maxBytes {
		return &apiError{status: http.StatusRequestEntityTooLarge, code: "request_too_large", message: "request body is too large"}
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		return decodeError(err)
	}
	var trailing any
	if err := dec.Decode(&trailing); err != io.EOF {
		if err == nil {
			return &apiError{status: http.StatusBadRequest, code: "invalid_json", message: "request body must contain exactly one JSON value"}
		}
		return decodeError(err)
	}
	return nil
}

func decodeError(err error) *apiError {
	var maxErr *http.MaxBytesError
	if errors.As(err, &maxErr) {
		return &apiError{status: http.StatusRequestEntityTooLarge, code: "request_too_large", message: "request body is too large"}
	}
	var syntaxErr *json.SyntaxError
	var typeErr *json.UnmarshalTypeError
	switch {
	case errors.As(err, &syntaxErr), errors.As(err, &typeErr):
		return &apiError{status: http.StatusBadRequest, code: "invalid_json", message: "invalid JSON body"}
	case strings.HasPrefix(err.Error(), "json: unknown field "):
		return &apiError{status: http.StatusBadRequest, code: "invalid_json", message: "invalid JSON body"}
	default:
		return &apiError{status: http.StatusBadRequest, code: "invalid_json", message: "invalid JSON body"}
	}
}

func writeRequestError(w http.ResponseWriter, e *apiError) {
	writeAPIError(w, e.status, e.code, e.message)
}

type responseRecorder struct {
	http.ResponseWriter
	status int
	bytes  int64
	wrote  bool
}

func (rw *responseRecorder) WriteHeader(status int) {
	if rw.wrote {
		return
	}
	rw.status = status
	rw.wrote = true
	rw.ResponseWriter.WriteHeader(status)
}

func (rw *responseRecorder) Write(p []byte) (int, error) {
	if !rw.wrote {
		rw.WriteHeader(http.StatusOK)
	}
	n, err := rw.ResponseWriter.Write(p)
	rw.bytes += int64(n)
	return n, err
}

func (rw *responseRecorder) Flush() {
	if !rw.wrote {
		rw.WriteHeader(http.StatusOK)
	}
	if flusher, ok := rw.ResponseWriter.(http.Flusher); ok {
		flusher.Flush()
	}
}

func (rw *responseRecorder) Unwrap() http.ResponseWriter { return rw.ResponseWriter }
