package main

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"errors"
	"net/http"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"golang.org/x/crypto/bcrypt"
)

const prehashedPasswordPrefix = "$reinchat-sha256$"

type credentialsRequest struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type authUser struct {
	ID        int64
	Username  string
	Level     int
	TokenHash [32]byte
}

var errUnauthorized = errors.New("unauthorized")

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req credentialsRequest
	if e := decodeJSONBody(w, r, &req, maxAuthJSONBytes); e != nil {
		writeRequestError(w, e)
		return
	}

	username, usernameLower, password, e := validateRegistration(req.Username, req.Password)
	if e != nil {
		writeRequestError(w, e)
		return
	}

	passwordHash, err := hashPassword(password, s.cfg.BcryptCost)
	if err != nil {
		s.internalError(w, r, err)
		return
	}

	now := s.now()
	token, tokenHash, err := generateToken()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	expiresAt := now.Add(s.cfg.TokenTTL)

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer func() { _ = tx.Rollback() }()

	res, err := tx.ExecContext(ctx,
		`INSERT INTO users (username, username_lower, password_hash, created_at)
		 VALUES (?, ?, ?, ?) ON CONFLICT(username_lower) DO NOTHING`,
		username, usernameLower, passwordHash, now.UnixMilli())
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	affected, err := res.RowsAffected()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if affected == 0 {
		writeAPIError(w, http.StatusConflict, "username_taken", "username is already taken")
		return
	}
	userID, err := res.LastInsertId()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if _, err := tx.ExecContext(ctx,
		`INSERT INTO tokens (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)`,
		tokenHash[:], userID, now.UnixMilli(), expiresAt.UnixMilli()); err != nil {
		s.internalError(w, r, err)
		return
	}
	if err := tx.Commit(); err != nil {
		s.internalError(w, r, err)
		return
	}

	s.cleanupExpiredTokens(r.Context())
	writeJSON(w, http.StatusCreated, AuthResponse{
		Token:     token,
		ExpiresAt: expiresAt.UnixMilli(),
		User:      UserDTO{Username: username, Level: 1},
	})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req credentialsRequest
	if e := decodeJSONBody(w, r, &req, maxAuthJSONBytes); e != nil {
		writeRequestError(w, e)
		return
	}

	username := strings.TrimSpace(req.Username)
	usernameLower := strings.ToLower(username)

	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()

	var userID int64
	var storedUsername, passwordHash string
	var level int
	err := s.db.QueryRowContext(ctx,
		`SELECT id, username, password_hash, level FROM users WHERE username_lower = ?`,
		usernameLower).Scan(&userID, &storedUsername, &passwordHash, &level)
	if errors.Is(err, sql.ErrNoRows) {
		// Burn the same bcrypt cost as a real account to reduce timing leakage.
		_ = bcrypt.CompareHashAndPassword(s.dummyHash, bcryptInput(req.Password))
		writeGenericLoginError(w)
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if !verifyPassword(passwordHash, req.Password) {
		writeGenericLoginError(w)
		return
	}

	now := s.now()
	token, tokenHash, err := generateToken()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	expiresAt := now.Add(s.cfg.TokenTTL)
	if _, err := s.db.ExecContext(ctx,
		`INSERT INTO tokens (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)`,
		tokenHash[:], userID, now.UnixMilli(), expiresAt.UnixMilli()); err != nil {
		s.internalError(w, r, err)
		return
	}

	s.cleanupExpiredTokens(r.Context())
	writeJSON(w, http.StatusOK, AuthResponse{
		Token:     token,
		ExpiresAt: expiresAt.UnixMilli(),
		User:      UserDTO{Username: storedUsername, Level: level},
	})
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	writeJSON(w, http.StatusOK, MeResponse{User: UserDTO{Username: user.Username, Level: user.Level}})
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if _, err := s.db.ExecContext(r.Context(),
		`DELETE FROM tokens WHERE token_hash = ?`, user.TokenHash[:]); err != nil {
		s.internalError(w, r, err)
		return
	}
	writeNoContent(w)
}

func (s *Server) requireAuth(w http.ResponseWriter, r *http.Request) (*authUser, bool) {
	user, err := s.authenticateRequest(r)
	if errors.Is(err, errUnauthorized) {
		w.Header().Set("WWW-Authenticate", "Bearer")
		writeAPIError(w, http.StatusUnauthorized, "unauthorized", "authentication required")
		return nil, false
	}
	if err != nil {
		s.internalError(w, r, err)
		return nil, false
	}
	return user, true
}

func (s *Server) authenticateRequest(r *http.Request) (*authUser, error) {
	header := r.Header.Get("Authorization")
	scheme, token, found := strings.Cut(header, " ")
	if !found || !strings.EqualFold(scheme, "Bearer") {
		return nil, errUnauthorized
	}
	token = strings.TrimSpace(token)
	if token == "" || len(token) > 1024 {
		return nil, errUnauthorized
	}
	tokenHash := hashToken(token)

	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	var user authUser
	err := s.db.QueryRowContext(ctx,
		`SELECT u.id, u.username, u.level
		 FROM tokens t JOIN users u ON u.id = t.user_id
		 WHERE t.token_hash = ? AND t.expires_at > ?`,
		tokenHash[:], s.now().UnixMilli()).Scan(&user.ID, &user.Username, &user.Level)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, errUnauthorized
	}
	if err != nil {
		return nil, err
	}
	user.TokenHash = tokenHash
	return &user, nil
}

func (s *Server) cleanupExpiredTokens(ctx context.Context) {
	cleanupCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if _, err := s.db.ExecContext(cleanupCtx, `DELETE FROM tokens WHERE expires_at <= ?`, s.now().UnixMilli()); err != nil {
		s.logger.Printf("token cleanup failed: %v", err)
	}
}

func validateRegistration(rawUsername, password string) (username, usernameLower, validatedPassword string, e *apiError) {
	username = strings.TrimSpace(rawUsername)
	if err := validateUsername(username); err != nil {
		return "", "", "", err
	}
	if err := validatePassword(password); err != nil {
		return "", "", "", err
	}
	return username, strings.ToLower(username), password, nil
}

func validateUsername(username string) *apiError {
	count := 0
	for _, r := range username {
		count++
		if unicode.IsLetter(r) || unicode.IsDigit(r) || r == '_' || r == '-' || r == '.' {
			continue
		}
		return invalidField("username", "username may only contain letters, digits, and _-.")
	}
	if count < 3 || count > 32 {
		return invalidField("username", "username must be between 3 and 32 characters")
	}
	return nil
}

func validatePassword(password string) *apiError {
	chars := utf8.RuneCountInString(password)
	if chars < 8 || chars > 128 {
		return invalidField("password", "password must be between 8 and 128 characters")
	}
	return nil
}

// hashPassword keeps regular bcrypt for passwords within bcrypt's 72-byte
// limit. Longer passwords are SHA-256 pre-hashed and then bcrypt-hashed, so the
// API can accept the contract's 8-128 character range without ever passing more
// than 72 bytes to bcrypt. The marker makes the scheme explicit and lets future
// code migrate safely.
func hashPassword(password string, cost int) (string, error) {
	raw := []byte(password)
	if len(raw) <= 72 {
		hash, err := bcrypt.GenerateFromPassword(raw, cost)
		return string(hash), err
	}
	hash, err := bcrypt.GenerateFromPassword(prehashedPasswordBytes(password), cost)
	if err != nil {
		return "", err
	}
	return prehashedPasswordPrefix + string(hash), nil
}

func verifyPassword(storedHash, password string) bool {
	if strings.HasPrefix(storedHash, prehashedPasswordPrefix) {
		bcryptHash := strings.TrimPrefix(storedHash, prehashedPasswordPrefix)
		return bcrypt.CompareHashAndPassword([]byte(bcryptHash), prehashedPasswordBytes(password)) == nil
	}
	return bcrypt.CompareHashAndPassword([]byte(storedHash), bcryptInput(password)) == nil
}

func bcryptInput(password string) []byte {
	if len(password) > 72 {
		// A standard bcrypt hash cannot match this input, but hashing it to a
		// fixed length keeps the comparison path expensive and non-revealing.
		return prehashedPasswordBytes(password)
	}
	return []byte(password)
}

func prehashedPasswordBytes(password string) []byte {
	digest := sha256.Sum256([]byte(password))
	return []byte(base64.RawStdEncoding.EncodeToString(digest[:]))
}

func invalidField(field, message string) *apiError {
	return &apiError{status: http.StatusBadRequest, code: "validation_error", message: message}
}

func writeGenericLoginError(w http.ResponseWriter) {
	writeAPIError(w, http.StatusUnauthorized, "invalid_credentials", "invalid username or password")
}
