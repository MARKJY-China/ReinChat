package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"unicode"
	"unicode/utf8"
)

const (
	maxConversationMessages = 1000
	maxMessageContentBytes  = 1 << 20
	maxMessageErrorBytes    = 10_000
	maxMessageImages        = 20
	maxConversationImages   = 1000
	maxTitleRunes           = 500
	maxMessageIDRunes       = 200
	maxImageIDRunes         = 200
)

func (s *Server) handleCreateConversation(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	var conversation ConversationDTO
	if e := decodeJSONBody(w, r, &conversation, maxConversationBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	if conversation.Messages == nil {
		conversation.Messages = []MessageDTO{}
	}
	conversation.Settings = normalizeConversationSettings(conversation.Settings)
	if e := validateConversationSettings(conversation.Settings); e != nil {
		writeRequestError(w, e)
		return
	}
	if !isUUID(conversation.ID) {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "conversation id must be a UUID")
		return
	}
	if e := validateConversationFields(conversation.Title, conversation.CreatedAt, conversation.UpdatedAt, conversation.Messages); e != nil {
		writeRequestError(w, e)
		return
	}
	messages, e := s.validateMessageImages(r.Context(), user.ID, conversation.Messages)
	if e != nil {
		writeRequestError(w, e)
		return
	}
	stored, preview, err := encodeMessages(messages)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	settingsJSON, err := json.Marshal(conversation.Settings)
	if err != nil {
		s.internalError(w, r, err)
		return
	}

	res, err := s.db.ExecContext(r.Context(),
		`INSERT INTO conversations
		 (user_id, id, title, created_at, updated_at, version, messages_json, message_count, preview, settings_json)
		 VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?, ?)
		 ON CONFLICT(user_id, id) DO NOTHING`,
		user.ID, conversation.ID, conversation.Title, conversation.CreatedAt, conversation.UpdatedAt,
		stored, len(messages), preview, settingsJSON)
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
		writeAPIError(w, http.StatusConflict, "conversation_exists", "a conversation with this id already exists")
		return
	}

	conversation.Version = 1
	conversation.Messages = messages
	writeJSON(w, http.StatusCreated, conversation)
	s.events.publish(user.ID, SyncEvent{
		Type:           "conversation.created",
		ConversationID: conversation.ID,
		Version:        1,
		UpdatedAt:      conversation.UpdatedAt,
	})
}

func (s *Server) handleListConversations(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	rows, err := s.db.QueryContext(r.Context(),
		`SELECT id, title, created_at, updated_at, version, message_count, preview
		 FROM conversations WHERE user_id = ?
		 ORDER BY updated_at DESC, id
		 LIMIT 500`, user.ID)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer rows.Close()

	summaries := make([]SummaryDTO, 0)
	for rows.Next() {
		var item SummaryDTO
		if err := rows.Scan(&item.ID, &item.Title, &item.CreatedAt, &item.UpdatedAt,
			&item.Version, &item.MessageCount, &item.Preview); err != nil {
			s.internalError(w, r, err)
			return
		}
		summaries = append(summaries, item)
	}
	if err := rows.Err(); err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, ConversationsResponse{Conversations: summaries})
}

func (s *Server) handleConversationItem(w http.ResponseWriter, r *http.Request, conversationID string) {
	switch r.Method {
	case http.MethodGet, http.MethodPut, http.MethodDelete:
	default:
		methodNotAllowed(w, http.MethodGet, http.MethodPut, http.MethodDelete)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	switch r.Method {
	case http.MethodGet:
		s.getConversationHandler(w, r, user, conversationID)
	case http.MethodPut:
		s.updateConversationHandler(w, r, user, conversationID)
	case http.MethodDelete:
		s.deleteConversationHandler(w, r, user, conversationID)
	}
}

func (s *Server) getConversationHandler(w http.ResponseWriter, r *http.Request, user *authUser, conversationID string) {
	if !isUUID(conversationID) {
		writeAPIError(w, http.StatusNotFound, "conversation_not_found", "conversation not found")
		return
	}
	conversation, err := s.loadConversation(r.Context(), user.ID, conversationID)
	if errors.Is(err, sql.ErrNoRows) {
		writeAPIError(w, http.StatusNotFound, "conversation_not_found", "conversation not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, conversation)
}

func (s *Server) updateConversationHandler(w http.ResponseWriter, r *http.Request, user *authUser, conversationID string) {
	if !isUUID(conversationID) {
		writeAPIError(w, http.StatusNotFound, "conversation_not_found", "conversation not found")
		return
	}
	var req UpdateConversationRequest
	if e := decodeJSONBody(w, r, &req, maxConversationBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	if req.Messages == nil {
		req.Messages = []MessageDTO{}
	}
	var settingsJSON []byte
	if req.Settings != nil {
		req.Settings = normalizeConversationSettings(req.Settings)
		if e := validateConversationSettings(req.Settings); e != nil {
			writeRequestError(w, e)
			return
		}
		var marshalErr error
		settingsJSON, marshalErr = json.Marshal(req.Settings)
		if marshalErr != nil {
			s.internalError(w, r, marshalErr)
			return
		}
	}
	if req.BaseVersion < 1 {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "baseVersion must be at least 1")
		return
	}
	if e := validateConversationFields(req.Title, req.CreatedAt, req.UpdatedAt, req.Messages); e != nil {
		writeRequestError(w, e)
		return
	}
	messages, e := s.validateMessageImages(r.Context(), user.ID, req.Messages)
	if e != nil {
		writeRequestError(w, e)
		return
	}
	stored, preview, err := encodeMessages(messages)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	updatedAt := s.now().UnixMilli()
	res, err := s.db.ExecContext(r.Context(),
		`UPDATE conversations SET title = ?, created_at = ?, updated_at = ?, version = version + 1,
		 messages_json = ?, message_count = ?, preview = ?,
		 settings_json = COALESCE(?, settings_json)
		 WHERE user_id = ? AND id = ? AND version = ?`,
		req.Title, req.CreatedAt, updatedAt, stored, len(messages), preview,
		settingsJSON, user.ID, conversationID, req.BaseVersion)
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
		current, loadErr := s.loadConversation(r.Context(), user.ID, conversationID)
		if errors.Is(loadErr, sql.ErrNoRows) {
			writeAPIError(w, http.StatusNotFound, "conversation_not_found", "conversation not found")
			return
		}
		if loadErr != nil {
			s.internalError(w, r, loadErr)
			return
		}
		writeJSON(w, http.StatusConflict, VersionConflictResponse{
			Error:        ErrorBody{Code: "version_conflict", Message: "conversation was updated by another client"},
			Conversation: *current,
		})
		return
	}

	var responseSettings *ConversationSettings
	if req.Settings != nil {
		responseSettings = req.Settings
	} else if current, loadErr := s.loadConversation(r.Context(), user.ID, conversationID); loadErr == nil {
		responseSettings = current.Settings
	}
	writeJSON(w, http.StatusOK, ConversationDTO{
		ID:        conversationID,
		Title:     req.Title,
		CreatedAt: req.CreatedAt,
		UpdatedAt: updatedAt,
		Version:   req.BaseVersion + 1,
		Messages:  messages,
		Settings:  responseSettings,
	})
	s.events.publish(user.ID, SyncEvent{
		Type:           "conversation.updated",
		ConversationID: conversationID,
		Version:        req.BaseVersion + 1,
		UpdatedAt:      updatedAt,
	})
}

func (s *Server) deleteConversationHandler(w http.ResponseWriter, r *http.Request, user *authUser, conversationID string) {
	if _, err := s.db.ExecContext(r.Context(),
		`DELETE FROM conversations WHERE user_id = ? AND id = ?`, user.ID, conversationID); err != nil {
		s.internalError(w, r, err)
		return
	}
	writeNoContent(w)
	s.events.publish(user.ID, SyncEvent{
		Type:           "conversation.deleted",
		ConversationID: conversationID,
		Deleted:        true,
	})
}

func (s *Server) loadConversation(ctx context.Context, userID int64, conversationID string) (*ConversationDTO, error) {
	var conversation ConversationDTO
	var stored, settingsStored []byte
	err := s.db.QueryRowContext(ctx,
		`SELECT id, title, created_at, updated_at, version, messages_json, settings_json
		 FROM conversations WHERE user_id = ? AND id = ?`, userID, conversationID).
		Scan(&conversation.ID, &conversation.Title, &conversation.CreatedAt,
			&conversation.UpdatedAt, &conversation.Version, &stored, &settingsStored)
	if err != nil {
		return nil, err
	}
	if err := json.Unmarshal(stored, &conversation.Messages); err != nil {
		return nil, fmt.Errorf("decode stored conversation: %w", err)
	}
	conversation.Settings = &ConversationSettings{ReasoningEffort: "high", Search: true}
	if len(settingsStored) > 0 {
		_ = json.Unmarshal(settingsStored, conversation.Settings)
		conversation.Settings = normalizeConversationSettings(conversation.Settings)
	}
	for i := range conversation.Messages {
		if conversation.Messages[i].Images == nil {
			conversation.Messages[i].Images = []ImageDTO{}
		}
	}
	if conversation.Messages == nil {
		conversation.Messages = []MessageDTO{}
	}
	return &conversation, nil
}

func normalizeConversationSettings(settings *ConversationSettings) *ConversationSettings {
	if settings == nil {
		return &ConversationSettings{ReasoningEffort: "high", Search: true}
	}
	copy := *settings
	if copy.ReasoningEffort == "" {
		copy.ReasoningEffort = "high"
	}
	return &copy
}

func validateConversationSettings(settings *ConversationSettings) *apiError {
	if settings == nil {
		return nil
	}
	if utf8.RuneCountInString(settings.ProviderID) > maxMessageIDRunes {
		return invalidField("settings", "providerId is too long")
	}
	if utf8.RuneCountInString(settings.Model) > 500 {
		return invalidField("settings", "model is too long")
	}
	switch settings.ReasoningEffort {
	case "", "low", "medium", "high", "max":
	default:
		return invalidField("settings", "unsupported reasoningEffort")
	}
	return nil
}

func validateConversationFields(title string, createdAt, updatedAt int64, messages []MessageDTO) *apiError {
	if utf8.RuneCountInString(title) > maxTitleRunes {
		return invalidField("title", "title must be at most 500 characters")
	}
	if createdAt < 0 || updatedAt < 0 {
		return invalidField("timestamps", "timestamps must be non-negative epoch milliseconds")
	}
	if len(messages) > maxConversationMessages {
		return invalidField("messages", "a conversation may contain at most 1000 messages")
	}
	for i := range messages {
		message := &messages[i]
		if message.ID == "" {
			return invalidField("messages", "message id must not be empty")
		}
		if utf8.RuneCountInString(message.ID) > maxMessageIDRunes {
			return invalidField("messages", "message id must be at most 200 characters")
		}
		if hasControlRune(message.ID) {
			return invalidField("messages", "message id contains control characters")
		}
		if message.Role != "user" && message.Role != "assistant" {
			return invalidField("messages", "message role must be user or assistant")
		}
		if len(message.Content) > maxMessageContentBytes {
			return invalidField("messages", "message content must be at most 1 MiB")
		}
		if message.CreatedAt < 0 {
			return invalidField("messages", "message createdAt must be non-negative epoch milliseconds")
		}
		if message.Status != "COMPLETE" && message.Status != "STOPPED" && message.Status != "ERROR" {
			return invalidField("messages", "message status must be COMPLETE, STOPPED, or ERROR")
		}
		if message.Error != nil && len(*message.Error) > maxMessageErrorBytes {
			return invalidField("messages", "message error must be at most 10000 bytes")
		}
		if len(message.Images) > maxMessageImages {
			return invalidField("messages", "a message may reference at most 20 images")
		}
		if message.Images == nil {
			message.Images = []ImageDTO{}
		}
		for j := range message.Images {
			image := &message.Images[j]
			if image.ID == "" || utf8.RuneCountInString(image.ID) > maxImageIDRunes || hasControlRune(image.ID) {
				return invalidField("images", "image id must be between 1 and 200 characters")
			}
			if image.RemoteID != nil && *image.RemoteID != "" {
				if !isUUID(*image.RemoteID) {
					return &apiError{status: http.StatusBadRequest, code: "invalid_image", message: "one or more referenced images are invalid"}
				}
			}
			if image.MimeType != "image/jpeg" && image.MimeType != "image/png" && image.MimeType != "image/webp" {
				return invalidField("images", "image mimeType must be image/jpeg, image/png, or image/webp")
			}
			if image.Width < 1 || image.Width > 100_000 || image.Height < 1 || image.Height > 100_000 {
				return invalidField("images", "image width and height must be between 1 and 100000")
			}
		}
	}
	return nil
}

func (s *Server) validateMessageImages(ctx context.Context, userID int64, messages []MessageDTO) ([]MessageDTO, *apiError) {
	totalImages := 0
	remoteIDs := make(map[string]struct{})
	for i := range messages {
		totalImages += len(messages[i].Images)
		for j := range messages[i].Images {
			if id := messages[i].Images[j].RemoteID; id != nil && *id != "" {
				remoteIDs[*id] = struct{}{}
			}
		}
	}
	if totalImages > maxConversationImages {
		return nil, invalidField("images", "a conversation may reference at most 1000 images")
	}
	if len(remoteIDs) == 0 {
		return messages, nil
	}

	placeholders := make([]string, 0, len(remoteIDs))
	args := make([]any, 0, len(remoteIDs)+1)
	args = append(args, userID)
	for id := range remoteIDs {
		placeholders = append(placeholders, "?")
		args = append(args, id)
	}
	query := `SELECT id FROM files WHERE user_id = ? AND id IN (` + strings.Join(placeholders, ",") + `)`
	rows, err := s.db.QueryContext(ctx, query, args...)
	if err != nil {
		s.logger.Printf("image ownership lookup failed: %v", err)
		return nil, &apiError{status: http.StatusInternalServerError, code: "internal_error", message: "internal server error"}
	}
	defer rows.Close()
	found := 0
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			s.logger.Printf("image ownership scan failed: %v", err)
			return nil, &apiError{status: http.StatusInternalServerError, code: "internal_error", message: "internal server error"}
		}
		found++
	}
	if err := rows.Err(); err != nil {
		s.logger.Printf("image ownership query failed: %v", err)
		return nil, &apiError{status: http.StatusInternalServerError, code: "internal_error", message: "internal server error"}
	}
	if found != len(remoteIDs) {
		return nil, &apiError{status: http.StatusBadRequest, code: "invalid_image", message: "one or more referenced images do not exist or are not owned by this user"}
	}
	return messages, nil
}

func encodeMessages(messages []MessageDTO) ([]byte, string, error) {
	if messages == nil {
		messages = []MessageDTO{}
	}
	stored, err := json.Marshal(messages)
	if err != nil {
		return nil, "", err
	}
	return stored, makePreview(messages), nil
}

func makePreview(messages []MessageDTO) string {
	for i := len(messages) - 1; i >= 0; i-- {
		preview := normalizePreviewText(messages[i].Content)
		if preview != "" {
			return truncateRunes(preview, 200)
		}
	}
	return ""
}

func hasControlRune(s string) bool {
	for _, r := range s {
		if unicode.IsControl(r) {
			return true
		}
	}
	return false
}
