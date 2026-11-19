package main

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"
	"unicode/utf8"
)

type ConversationTitleRequest struct {
	ProviderID string `json:"providerId"`
	Model      string `json:"model"`
	Text       string `json:"text"`
}

const titlePrompt = "你是一个会话标题生成器。根据用户的第一条消息，生成一个具体、简洁的中文标题，最多 18 个汉字。只输出标题本身，不要引号、句号、解释或 Markdown。"

func (s *Server) handleConversationTitle(w http.ResponseWriter, r *http.Request, conversationID string) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w, http.MethodPost)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if _, err := s.loadConversation(r.Context(), user.ID, conversationID); err != nil {
		writeAPIError(w, http.StatusNotFound, "not_found", "conversation not found")
		return
	}
	var req ConversationTitleRequest
	if e := decodeJSONBody(w, r, &req, 64<<10); e != nil {
		writeRequestError(w, e)
		return
	}
	req.Text = strings.TrimSpace(req.Text)
	if req.Text == "" || strings.TrimSpace(req.ProviderID) == "" {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "providerId and text are required")
		return
	}
	if utf8.RuneCountInString(req.Text) > 4000 {
		req.Text = string([]rune(req.Text)[:4000])
	}
	provider, err := s.loadRelayProvider(r.Context(), user.ID, req.ProviderID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			writeAPIError(w, http.StatusNotFound, "provider_not_found", "provider not found")
			return
		}
		s.internalError(w, r, err)
		return
	}
	model := strings.TrimSpace(req.Model)
	if model == "" {
		model = provider.Model
	}
	if model == "" {
		writeAPIError(w, http.StatusBadRequest, "invalid_model", "model is required")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 20*time.Second)
	defer cancel()
	title, err := generateConversationTitle(ctx, provider, model, req.Text)
	if err != nil {
		writeAPIError(w, http.StatusBadGateway, "title_failed", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"title": title})
}

func generateConversationTitle(ctx context.Context, p *relayProvider, model, firstMessage string) (string, error) {
	base := strings.TrimRight(strings.TrimSpace(p.BaseURL), "/")
	if base == "" {
		return "", errors.New("provider base URL is empty")
	}
	var endpoint string
	var body any
	if p.Protocol == "RESPONSES" {
		endpoint = base + "/responses"
		body = map[string]any{
			"model":             model,
			"stream":            false,
			"instructions":      titlePrompt,
			"input":             []map[string]any{{"role": "user", "content": firstMessage}},
			"max_output_tokens": 96,
		}
	} else {
		endpoint = base + "/chat/completions"
		body = map[string]any{
			"model":  model,
			"stream": false,
			"messages": []map[string]any{
				{"role": "system", "content": titlePrompt},
				{"role": "user", "content": firstMessage},
			},
			"max_tokens":  96,
			"temperature": 0.2,
		}
	}
	raw, err := json.Marshal(body)
	if err != nil {
		return "", err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(raw))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/json")
	if p.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+p.APIKey)
	}
	resp, err := (&http.Client{Timeout: 20 * time.Second}).Do(req)
	if err != nil {
		return "", errors.New("title provider unavailable")
	}
	defer resp.Body.Close()
	responseBody, _ := io.ReadAll(io.LimitReader(resp.Body, 64<<10))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return "", errors.New(upstreamError(resp.StatusCode, responseBody))
	}
	var decoded map[string]any
	if err := json.Unmarshal(responseBody, &decoded); err != nil {
		return "", errors.New("invalid title response")
	}
	title := extractTitle(decoded, p.Protocol)
	title = strings.TrimSpace(strings.Trim(title, "`\"'“”‘’《》#*- "))
	title = strings.Join(strings.Fields(title), " ")
	if title == "" {
		return "", errors.New("empty title response")
	}
	runes := []rune(title)
	if len(runes) > 40 {
		title = string(runes[:40])
	}
	return title, nil
}

func extractTitle(decoded map[string]any, protocol string) string {
	if protocol == "RESPONSES" {
		if output, ok := decoded["output_text"].(string); ok {
			return output
		}
		if output, ok := decoded["output"].([]any); ok {
			var b strings.Builder
			for _, item := range output {
				obj, _ := item.(map[string]any)
				content, _ := obj["content"].([]any)
				for _, part := range content {
					p, _ := part.(map[string]any)
					if text, _ := p["text"].(string); text != "" {
						b.WriteString(text)
					}
				}
			}
			return b.String()
		}
	}
	choices, _ := decoded["choices"].([]any)
	if len(choices) == 0 {
		return ""
	}
	choice, _ := choices[0].(map[string]any)
	message, _ := choice["message"].(map[string]any)
	text, _ := message["content"].(string)
	return text
}
