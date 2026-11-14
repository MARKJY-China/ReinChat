package main

import (
	"bytes"
	"encoding/base64"
	"encoding/json"
	"io"
	"log"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"golang.org/x/crypto/bcrypt"
)

type testEnv struct {
	t      *testing.T
	app    *Server
	server *httptest.Server
	client *http.Client
}

type testResponse struct {
	status int
	header http.Header
	body   []byte
}

func newTestEnv(t *testing.T) *testEnv {
	t.Helper()
	app, err := NewServer(Config{
		DataDir:        t.TempDir(),
		MasterKey:      bytes.Repeat([]byte{0x42}, 32),
		TokenTTL:       30 * 24 * time.Hour,
		BcryptCost:     bcrypt.MinCost,
		AuthRateLimit:  10_000,
		AuthRateWindow: time.Minute,
		Logger:         log.New(io.Discard, "", 0),
	})
	if err != nil {
		t.Fatalf("NewServer: %v", err)
	}
	server := httptest.NewServer(app)
	env := &testEnv{
		t:      t,
		app:    app,
		server: server,
		client: server.Client(),
	}
	t.Cleanup(func() {
		server.Close()
		_ = app.Close()
	})
	return env
}

func (e *testEnv) doJSON(method, path, token string, payload any) testResponse {
	e.t.Helper()
	var body io.Reader
	if payload != nil {
		encoded, err := json.Marshal(payload)
		if err != nil {
			e.t.Fatalf("marshal request: %v", err)
		}
		body = bytes.NewReader(encoded)
	}
	req, err := http.NewRequest(method, e.server.URL+path, body)
	if err != nil {
		e.t.Fatalf("create request: %v", err)
	}
	if payload != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := e.client.Do(req)
	if err != nil {
		e.t.Fatalf("%s %s: %v", method, path, err)
	}
	defer resp.Body.Close()
	data, err := io.ReadAll(resp.Body)
	if err != nil {
		e.t.Fatalf("read response: %v", err)
	}
	return testResponse{status: resp.StatusCode, header: resp.Header.Clone(), body: data}
}

func (e *testEnv) doMultipart(path, token, fieldName, fileName string, data []byte) testResponse {
	e.t.Helper()
	var body bytes.Buffer
	writer := multipart.NewWriter(&body)
	part, err := writer.CreateFormFile(fieldName, fileName)
	if err != nil {
		e.t.Fatalf("create multipart field: %v", err)
	}
	if _, err := part.Write(data); err != nil {
		e.t.Fatalf("write multipart field: %v", err)
	}
	if err := writer.Close(); err != nil {
		e.t.Fatalf("close multipart: %v", err)
	}
	req, err := http.NewRequest(http.MethodPost, e.server.URL+path, &body)
	if err != nil {
		e.t.Fatalf("create upload request: %v", err)
	}
	req.Header.Set("Content-Type", writer.FormDataContentType())
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := e.client.Do(req)
	if err != nil {
		e.t.Fatalf("upload: %v", err)
	}
	defer resp.Body.Close()
	payload, err := io.ReadAll(resp.Body)
	if err != nil {
		e.t.Fatalf("read upload response: %v", err)
	}
	return testResponse{status: resp.StatusCode, header: resp.Header.Clone(), body: payload}
}

func (e *testEnv) register(username, password string) AuthResponse {
	e.t.Helper()
	resp := e.doJSON(http.MethodPost, "/api/v1/auth/register", "", credentialsRequest{
		Username: username,
		Password: password,
	})
	requireStatus(e.t, resp, http.StatusCreated)
	var auth AuthResponse
	decodeBody(e.t, resp, &auth)
	if auth.Token == "" || auth.User.Username != username || auth.ExpiresAt <= time.Now().UnixMilli() {
		e.t.Fatalf("unexpected auth response: %+v", auth)
	}
	return auth
}

func requireStatus(t *testing.T, resp testResponse, want int) {
	t.Helper()
	if resp.status != want {
		t.Fatalf("status = %d, want %d; body=%s", resp.status, want, string(resp.body))
	}
}

func decodeBody(t *testing.T, resp testResponse, dst any) {
	t.Helper()
	if err := json.Unmarshal(resp.body, dst); err != nil {
		t.Fatalf("decode response body %q: %v", string(resp.body), err)
	}
}

func errorCode(t *testing.T, resp testResponse) string {
	t.Helper()
	var envelope ErrorResponse
	decodeBody(t, resp, &envelope)
	return envelope.Error.Code
}

var testPNG, _ = base64.StdEncoding.DecodeString(
	"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
)
