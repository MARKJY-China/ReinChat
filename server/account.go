package main

import (
	"context"
	"database/sql"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"
)

type UpgradeRequestDTO struct {
	ID           string  `json:"id"`
	UserID       int64   `json:"userId"`
	Username     string  `json:"username,omitempty"`
	FromLevel    int     `json:"fromLevel"`
	ToLevel      int     `json:"toLevel"`
	Reason       string  `json:"reason"`
	Status       string  `json:"status"`
	RejectReason *string `json:"rejectReason,omitempty"`
	AdminNote    *string `json:"adminNote,omitempty"`
	ReviewedBy   *string `json:"reviewedBy,omitempty"`
	CreatedAt    int64   `json:"createdAt"`
	ReviewedAt   *int64  `json:"reviewedAt,omitempty"`
}

type AccountResponse struct {
	User              UserDTO             `json:"user"`
	PendingRequest    *UpgradeRequestDTO  `json:"pendingRequest,omitempty"`
	LatestRequests    []UpgradeRequestDTO `json:"latestRequests"`
	CanRequestUpgrade bool                `json:"canRequestUpgrade"`
	AgentAvailable    bool                `json:"agentAvailable"`
}

type UpgradeRequestCreate struct {
	Reason string `json:"reason"`
}

type UpgradeRequestList struct {
	Requests []UpgradeRequestDTO `json:"requests"`
}

type AdminTicketAction struct {
	Reason string `json:"reason"`
	Note   string `json:"note"`
}

type AdminUsersResponse struct {
	Users []AdminUserDTO `json:"users"`
}

type AdminUserDTO struct {
	ID        int64  `json:"id"`
	Username  string `json:"username"`
	Level     int    `json:"level"`
	CreatedAt int64  `json:"createdAt"`
	Pending   bool   `json:"pendingUpgrade"`
}

type AdminLevelRequest struct {
	Level int `json:"level"`
}

func (s *Server) handleAccount(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	requests, err := s.listUpgradeRequests(r.Context(), user.ID, 10)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	var pending *UpgradeRequestDTO
	for i := range requests {
		if requests[i].Status == "pending" {
			pending = &requests[i]
			break
		}
	}
	writeJSON(w, http.StatusOK, AccountResponse{
		User:              UserDTO{Username: user.Username, Level: user.Level},
		PendingRequest:    pending,
		LatestRequests:    requests,
		CanRequestUpgrade: user.Level < 2 && pending == nil,
		AgentAvailable:    strings.TrimSpace(s.cfg.DSHGatewayURL) != "",
	})
}

func (s *Server) handleUpgradeRequests(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	switch r.Method {
	case http.MethodGet:
		requests, err := s.listUpgradeRequests(r.Context(), user.ID, 50)
		if err != nil {
			s.internalError(w, r, err)
			return
		}
		writeJSON(w, http.StatusOK, UpgradeRequestList{Requests: requests})
	case http.MethodPost:
		var req UpgradeRequestCreate
		if e := decodeJSONBody(w, r, &req, maxAuthJSONBytes); e != nil {
			writeRequestError(w, e)
			return
		}
		reason := strings.TrimSpace(req.Reason)
		if n := utf8.RuneCountInString(reason); n < 10 || n > 2000 {
			writeAPIError(w, http.StatusBadRequest, "validation_error", "升级理由需要 10–2000 个字符")
			return
		}
		if user.Level >= 2 {
			writeAPIError(w, http.StatusConflict, "already_lv2", "当前账号已是 LV2")
			return
		}
		var pending int
		if err := s.db.QueryRowContext(r.Context(),
			`SELECT COUNT(*) FROM upgrade_requests WHERE user_id = ? AND status = 'pending'`,
			user.ID).Scan(&pending); err != nil {
			s.internalError(w, r, err)
			return
		}
		if pending > 0 {
			writeAPIError(w, http.StatusConflict, "request_pending", "已有待审核的升级申请")
			return
		}
		id, err := newUUID()
		if err != nil {
			s.internalError(w, r, err)
			return
		}
		now := s.now().UnixMilli()
		if _, err := s.db.ExecContext(r.Context(),
			`INSERT INTO upgrade_requests
			 (id, user_id, from_level, to_level, reason, status, created_at)
			 VALUES (?, ?, ?, 2, ?, 'pending', ?)`,
			id, user.ID, user.Level, reason, now); err != nil {
			s.internalError(w, r, err)
			return
		}
		ticket, err := s.loadUpgradeRequest(r.Context(), id, true)
		if err != nil {
			s.internalError(w, r, err)
			return
		}
		writeJSON(w, http.StatusCreated, ticket)
	default:
		methodNotAllowed(w, http.MethodGet, http.MethodPost)
	}
}

func (s *Server) listUpgradeRequests(ctx context.Context, userID int64, limit int) ([]UpgradeRequestDTO, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, user_id, from_level, to_level, reason, status, reject_reason,
		        admin_note, reviewed_by, created_at, reviewed_at
		 FROM upgrade_requests WHERE user_id = ?
		 ORDER BY created_at DESC LIMIT ?`, userID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	result := make([]UpgradeRequestDTO, 0)
	for rows.Next() {
		item, err := scanUpgradeRequest(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	return result, rows.Err()
}

func (s *Server) loadUpgradeRequest(ctx context.Context, id string, withUsername bool) (*UpgradeRequestDTO, error) {
	query := `SELECT r.id, r.user_id, r.from_level, r.to_level, r.reason, r.status,
	                 r.reject_reason, r.admin_note, r.reviewed_by, r.created_at, r.reviewed_at,
	                 u.username
	          FROM upgrade_requests r JOIN users u ON u.id = r.user_id WHERE r.id = ?`
	row := s.db.QueryRowContext(ctx, query, id)
	item := UpgradeRequestDTO{}
	var reject, note, reviewedBy sql.NullString
	var reviewedAt sql.NullInt64
	if err := row.Scan(&item.ID, &item.UserID, &item.FromLevel, &item.ToLevel, &item.Reason,
		&item.Status, &reject, &note, &reviewedBy, &item.CreatedAt, &reviewedAt, &item.Username); err != nil {
		return nil, err
	}
	item.RejectReason = nullableString(reject)
	item.AdminNote = nullableString(note)
	item.ReviewedBy = nullableString(reviewedBy)
	if reviewedAt.Valid {
		item.ReviewedAt = &reviewedAt.Int64
	}
	return &item, nil
}

func scanUpgradeRequest(rows *sql.Rows) (UpgradeRequestDTO, error) {
	item := UpgradeRequestDTO{}
	var reject, note, reviewedBy sql.NullString
	var reviewedAt sql.NullInt64
	err := rows.Scan(&item.ID, &item.UserID, &item.FromLevel, &item.ToLevel, &item.Reason,
		&item.Status, &reject, &note, &reviewedBy, &item.CreatedAt, &reviewedAt)
	if err != nil {
		return item, err
	}
	item.RejectReason = nullableString(reject)
	item.AdminNote = nullableString(note)
	item.ReviewedBy = nullableString(reviewedBy)
	if reviewedAt.Valid {
		item.ReviewedAt = &reviewedAt.Int64
	}
	return item, nil
}

func nullableString(value sql.NullString) *string {
	if !value.Valid {
		return nil
	}
	v := value.String
	return &v
}

func (s *Server) adminAuthorized(r *http.Request) bool {
	expected := strings.TrimSpace(s.cfg.AdminToken)
	if expected == "" {
		return false
	}
	provided := strings.TrimSpace(r.Header.Get("X-Admin-Token"))
	if len(provided) != len(expected) {
		return false
	}
	var mismatch byte
	for i := 0; i < len(expected); i++ {
		mismatch |= expected[i] ^ provided[i]
	}
	return mismatch == 0
}

func (s *Server) handleAdmin(w http.ResponseWriter, r *http.Request) {
	if !s.adminAuthorized(r) {
		writeAPIError(w, http.StatusUnauthorized, "admin_unauthorized", "admin token required")
		return
	}
	path := r.URL.Path
	switch {
	case path == "/api/admin/upgrade-requests" && r.Method == http.MethodGet:
		s.handleAdminListTickets(w, r)
	case path == "/api/admin/users" && r.Method == http.MethodGet:
		s.handleAdminListUsers(w, r)
	case strings.HasPrefix(path, "/api/admin/upgrade-requests/"):
		s.handleAdminTicketItem(w, r, strings.TrimPrefix(path, "/api/admin/upgrade-requests/"))
	case strings.HasPrefix(path, "/api/admin/users/"):
		s.handleAdminUserItem(w, r, strings.TrimPrefix(path, "/api/admin/users/"))
	default:
		writeAPIError(w, http.StatusNotFound, "not_found", "admin endpoint not found")
	}
}

func (s *Server) handleAdminListTickets(w http.ResponseWriter, r *http.Request) {
	status := strings.TrimSpace(r.URL.Query().Get("status"))
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	query := `SELECT r.id, r.user_id, r.from_level, r.to_level, r.reason, r.status,
	                 r.reject_reason, r.admin_note, r.reviewed_by, r.created_at, r.reviewed_at,
	                 u.username
	          FROM upgrade_requests r JOIN users u ON u.id = r.user_id`
	args := []any{}
	if status != "" {
		query += ` WHERE r.status = ?`
		args = append(args, status)
	}
	query += ` ORDER BY r.created_at DESC LIMIT 200`
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer rows.Close()
	result := make([]UpgradeRequestDTO, 0)
	for rows.Next() {
		item := UpgradeRequestDTO{}
		var reject, note, reviewedBy sql.NullString
		var reviewedAt sql.NullInt64
		if err := rows.Scan(&item.ID, &item.UserID, &item.FromLevel, &item.ToLevel, &item.Reason,
			&item.Status, &reject, &note, &reviewedBy, &item.CreatedAt, &reviewedAt, &item.Username); err != nil {
			s.internalError(w, r, err)
			return
		}
		item.RejectReason = nullableString(reject)
		item.AdminNote = nullableString(note)
		item.ReviewedBy = nullableString(reviewedBy)
		if reviewedAt.Valid {
			item.ReviewedAt = &reviewedAt.Int64
		}
		result = append(result, item)
	}
	writeJSON(w, http.StatusOK, UpgradeRequestList{Requests: result})
}

func (s *Server) handleAdminTicketItem(w http.ResponseWriter, r *http.Request, rest string) {
	parts := strings.Split(strings.Trim(rest, "/"), "/")
	if len(parts) != 2 || r.Method != http.MethodPost {
		writeAPIError(w, http.StatusNotFound, "not_found", "admin endpoint not found")
		return
	}
	id, action := parts[0], parts[1]
	if !isUUID(id) {
		writeAPIError(w, http.StatusNotFound, "not_found", "ticket not found")
		return
	}
	var req AdminTicketAction
	_ = decodeJSONBody(w, r, &req, maxAuthJSONBytes)
	note := strings.TrimSpace(req.Note)
	rejectReason := strings.TrimSpace(req.Reason)
	if action == "reject" && rejectReason == "" {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "拒绝理由不能为空")
		return
	}
	if action != "approve" && action != "reject" {
		writeAPIError(w, http.StatusNotFound, "not_found", "unknown action")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Second)
	defer cancel()
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer func() { _ = tx.Rollback() }()
	var userID, toLevel int
	var status string
	if err := tx.QueryRowContext(ctx,
		`SELECT user_id, to_level, status FROM upgrade_requests WHERE id = ?`, id).
		Scan(&userID, &toLevel, &status); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			writeAPIError(w, http.StatusNotFound, "not_found", "ticket not found")
			return
		}
		s.internalError(w, r, err)
		return
	}
	if status != "pending" {
		writeAPIError(w, http.StatusConflict, "already_reviewed", "该工单已处理")
		return
	}
	now := s.now().UnixMilli()
	reviewedBy := "admin"
	var reject, noteArg any
	if action == "approve" {
		status = "approved"
		noteArg = note
	} else {
		status = "rejected"
		reject = rejectReason
		noteArg = note
	}
	if _, err := tx.ExecContext(ctx,
		`UPDATE upgrade_requests SET status = ?, reject_reason = ?, admin_note = ?,
		 reviewed_by = ?, reviewed_at = ? WHERE id = ?`,
		status, reject, noteArg, reviewedBy, now, id); err != nil {
		s.internalError(w, r, err)
		return
	}
	if action == "approve" {
		if _, err := tx.ExecContext(ctx, `UPDATE users SET level = ? WHERE id = ?`, toLevel, userID); err != nil {
			s.internalError(w, r, err)
			return
		}
	}
	if err := tx.Commit(); err != nil {
		s.internalError(w, r, err)
		return
	}
	ticket, err := s.loadUpgradeRequest(r.Context(), id, true)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	s.events.publish(int64(userID), SyncEvent{Type: "account.updated"})
	writeJSON(w, http.StatusOK, ticket)
}

func (s *Server) handleAdminListUsers(w http.ResponseWriter, r *http.Request) {
	queryText := strings.TrimSpace(r.URL.Query().Get("q"))
	ctx, cancel := context.WithTimeout(r.Context(), 10*time.Second)
	defer cancel()
	query := `SELECT u.id, u.username, u.level, u.created_at,
	                 EXISTS(SELECT 1 FROM upgrade_requests r WHERE r.user_id = u.id AND r.status='pending')
	          FROM users u`
	args := []any{}
	if queryText != "" {
		query += ` WHERE u.username_lower LIKE ?`
		args = append(args, "%"+strings.ToLower(queryText)+"%")
	}
	query += ` ORDER BY u.id DESC LIMIT 200`
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer rows.Close()
	users := make([]AdminUserDTO, 0)
	for rows.Next() {
		var user AdminUserDTO
		var pending bool
		if err := rows.Scan(&user.ID, &user.Username, &user.Level, &user.CreatedAt, &pending); err != nil {
			s.internalError(w, r, err)
			return
		}
		user.Pending = pending
		users = append(users, user)
	}
	writeJSON(w, http.StatusOK, AdminUsersResponse{Users: users})
}

func (s *Server) handleAdminUserItem(w http.ResponseWriter, r *http.Request, rest string) {
	parts := strings.Split(strings.Trim(rest, "/"), "/")
	if len(parts) != 2 || parts[1] != "level" || r.Method != http.MethodPost {
		writeAPIError(w, http.StatusNotFound, "not_found", "admin endpoint not found")
		return
	}
	var req AdminLevelRequest
	if e := decodeJSONBody(w, r, &req, maxAuthJSONBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	if req.Level < 1 || req.Level > 2 {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "level 只能是 1 或 2")
		return
	}
	res, err := s.db.ExecContext(r.Context(), `UPDATE users SET level = ? WHERE id = ?`, req.Level, parts[0])
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	affected, _ := res.RowsAffected()
	if affected == 0 {
		writeAPIError(w, http.StatusNotFound, "not_found", "user not found")
		return
	}
	if userID, parseErr := strconv.ParseInt(parts[0], 10, 64); parseErr == nil {
		s.events.publish(userID, SyncEvent{Type: "account.updated"})
	}
	writeNoContent(w)
}
