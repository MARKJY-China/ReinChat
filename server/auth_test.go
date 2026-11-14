package main

import (
	"bytes"
	"net/http"
	"strings"
	"testing"
	"time"
)

func TestHealthAndUnauthorizedAccess(t *testing.T) {
	env := newTestEnv(t)

	health := env.doJSON(http.MethodGet, "/healthz", "", nil)
	requireStatus(t, health, http.StatusOK)
	if strings.TrimSpace(string(health.body)) != `{"status":"ok"}` {
		t.Fatalf("unexpected health body: %s", health.body)
	}

	unauthorized := env.doJSON(http.MethodGet, "/api/v1/providers", "", nil)
	requireStatus(t, unauthorized, http.StatusUnauthorized)
	if code := errorCode(t, unauthorized); code != "unauthorized" {
		t.Fatalf("error code = %q", code)
	}
	if unauthorized.header.Get("WWW-Authenticate") != "Bearer" {
		t.Fatalf("missing WWW-Authenticate header")
	}

	badToken := env.doJSON(http.MethodGet, "/api/v1/conversations", "not-a-real-token", nil)
	requireStatus(t, badToken, http.StatusUnauthorized)
}

func TestAuthRegisterLoginMeLogout(t *testing.T) {
	env := newTestEnv(t)
	before := time.Now().UnixMilli()
	auth := env.register("Alice_01", "correct horse battery staple")

	var passwordHash string
	if err := env.app.db.QueryRow(`SELECT password_hash FROM users WHERE username_lower = ?`, "alice_01").Scan(&passwordHash); err != nil {
		t.Fatalf("query password hash: %v", err)
	}
	if passwordHash == "correct horse battery staple" || !strings.HasPrefix(passwordHash, "$2") {
		t.Fatalf("password was not bcrypt-hashed")
	}
	var tokenCount int
	tokenHash := hashToken(auth.Token)
	if err := env.app.db.QueryRow(`SELECT COUNT(*) FROM tokens WHERE token_hash = ?`, tokenHash[:]).Scan(&tokenCount); err != nil {
		t.Fatalf("query token hash: %v", err)
	}
	if tokenCount != 1 {
		t.Fatalf("token hash count = %d, want 1", tokenCount)
	}
	var rawTokenStored int
	if err := env.app.db.QueryRow(`SELECT COUNT(*) FROM tokens WHERE token_hash = ?`, []byte(auth.Token)).Scan(&rawTokenStored); err != nil {
		t.Fatalf("query raw token: %v", err)
	}
	if rawTokenStored != 0 {
		t.Fatalf("raw bearer token was stored in the token hash column")
	}
	if auth.ExpiresAt < before+30*24*time.Hour.Milliseconds()-5_000 || auth.ExpiresAt > time.Now().Add(30*24*time.Hour).UnixMilli()+5_000 {
		t.Fatalf("unexpected token expiry: %d", auth.ExpiresAt)
	}

	duplicate := env.doJSON(http.MethodPost, "/api/v1/auth/register", "", credentialsRequest{
		Username: " alice_01 ", Password: "another password",
	})
	requireStatus(t, duplicate, http.StatusConflict)
	if code := errorCode(t, duplicate); code != "username_taken" {
		t.Fatalf("duplicate error code = %q", code)
	}

	me := env.doJSON(http.MethodGet, "/api/v1/auth/me", auth.Token, nil)
	requireStatus(t, me, http.StatusOK)
	var meBody MeResponse
	decodeBody(t, me, &meBody)
	if meBody.User.Username != "Alice_01" {
		t.Fatalf("me username = %q", meBody.User.Username)
	}

	login := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "ALICE_01", Password: "correct horse battery staple",
	})
	requireStatus(t, login, http.StatusOK)
	var loginAuth AuthResponse
	decodeBody(t, login, &loginAuth)
	if loginAuth.Token == "" || loginAuth.Token == auth.Token || loginAuth.User.Username != "Alice_01" {
		t.Fatalf("unexpected login response: %+v", loginAuth)
	}

	wrongPassword := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "Alice_01", Password: "wrong password",
	})
	unknownUser := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "nobody", Password: "wrong password",
	})
	requireStatus(t, wrongPassword, http.StatusUnauthorized)
	requireStatus(t, unknownUser, http.StatusUnauthorized)
	var wrongErr, unknownErr ErrorResponse
	decodeBody(t, wrongPassword, &wrongErr)
	decodeBody(t, unknownUser, &unknownErr)
	if wrongErr != unknownErr {
		t.Fatalf("login errors leak account existence: %+v vs %+v", wrongErr, unknownErr)
	}

	logout := env.doJSON(http.MethodPost, "/api/v1/auth/logout", loginAuth.Token, nil)
	requireStatus(t, logout, http.StatusNoContent)
	if len(logout.body) != 0 {
		t.Fatalf("logout response has a body: %q", logout.body)
	}

	afterLogout := env.doJSON(http.MethodGet, "/api/v1/auth/me", loginAuth.Token, nil)
	requireStatus(t, afterLogout, http.StatusUnauthorized)
	stillValid := env.doJSON(http.MethodGet, "/api/v1/auth/me", auth.Token, nil)
	requireStatus(t, stillValid, http.StatusOK)
}

func TestAuthRateLimitHTTP(t *testing.T) {
	env := newTestEnv(t)
	env.app.limiter = newRateLimiter(1, time.Minute, time.Now)

	first := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "nobody", Password: "wrong password",
	})
	requireStatus(t, first, http.StatusUnauthorized)
	limited := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "nobody", Password: "wrong password",
	})
	requireStatus(t, limited, http.StatusTooManyRequests)
	if code := errorCode(t, limited); code != "rate_limited" {
		t.Fatalf("rate limit code = %q", code)
	}
	if limited.header.Get("Retry-After") == "" {
		t.Fatal("missing Retry-After header")
	}
}

func TestExpiredTokenIsRejected(t *testing.T) {
	env := newTestEnv(t)
	auth := env.register("expiring_user", "expiring password")
	hash := hashToken(auth.Token)
	if _, err := env.app.db.Exec(`UPDATE tokens SET expires_at = 0 WHERE token_hash = ?`, hash[:]); err != nil {
		t.Fatalf("expire token: %v", err)
	}
	resp := env.doJSON(http.MethodGet, "/api/v1/auth/me", auth.Token, nil)
	requireStatus(t, resp, http.StatusUnauthorized)
}

func TestAuthValidation(t *testing.T) {
	env := newTestEnv(t)

	cases := []struct {
		username string
		password string
	}{
		{"ab", "long enough password"},
		{"bad name", "long enough password"},
		{"valid_name", "short"},
		{"valid_name2", strings.Repeat("x", 129)},
		{"名字", "long enough password"},
	}
	for _, tc := range cases {
		resp := env.doJSON(http.MethodPost, "/api/v1/auth/register", "", credentialsRequest{
			Username: tc.username, Password: tc.password,
		})
		requireStatus(t, resp, http.StatusBadRequest)
		if code := errorCode(t, resp); code != "validation_error" {
			t.Fatalf("validation code = %q", code)
		}
	}

	unknownField := env.doJSON(http.MethodPost, "/api/v1/auth/register", "",
		map[string]any{"username": "another_user", "password": "long enough password", "admin": true})
	requireStatus(t, unknownField, http.StatusBadRequest)

	unicodeUser := env.register("用户_01", "密码密码密码密码") // 8 Unicode characters, 24 bytes
	me := env.doJSON(http.MethodGet, "/api/v1/auth/me", unicodeUser.Token, nil)
	requireStatus(t, me, http.StatusOK)

	var raw []byte
	if err := env.app.db.QueryRow(`SELECT password_hash FROM users WHERE username = ?`, "用户_01").Scan(&raw); err != nil {
		t.Fatalf("query hash: %v", err)
	}
	if bytes.Contains(raw, []byte("密码密码密码密码")) {
		t.Fatalf("plaintext password found in database")
	}

	// bcrypt accepts at most 72 bytes, but the API contract allows 128
	// characters. Long passwords are pre-hashed and must still round trip.
	longPassword := strings.Repeat("x", 128)
	longUser := env.register("long_password_user", longPassword)
	longLogin := env.doJSON(http.MethodPost, "/api/v1/auth/login", "", credentialsRequest{
		Username: "long_password_user", Password: longPassword,
	})
	requireStatus(t, longLogin, http.StatusOK)
	var longAuth AuthResponse
	decodeBody(t, longLogin, &longAuth)
	if longAuth.Token == "" || longAuth.User.Username != "long_password_user" {
		t.Fatalf("long password login failed: %+v", longAuth)
	}
	me = env.doJSON(http.MethodGet, "/api/v1/auth/me", longUser.Token, nil)
	requireStatus(t, me, http.StatusOK)

	var longHash string
	if err := env.app.db.QueryRow(`SELECT password_hash FROM users WHERE username = ?`, "long_password_user").Scan(&longHash); err != nil {
		t.Fatalf("query long password hash: %v", err)
	}
	if !strings.HasPrefix(longHash, prehashedPasswordPrefix) {
		t.Fatalf("long password was not safely pre-hashed: %q", longHash)
	}
}
