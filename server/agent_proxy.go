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
)

type AgentRunRequest struct {
	ProviderID      string `json:"providerId"`
	Model           string `json:"model"`
	ReasoningEffort string `json:"reasoningEffort"`
	ConversationID  string `json:"conversationId"`
	Text            string `json:"text"`
}

type gatewayRunRequest struct {
	BaseURL         string `json:"baseUrl"`
	APIKey          string `json:"apiKey"`
	Model           string `json:"model"`
	ReasoningEffort string `json:"reasoningEffort"`
	SystemPrompt    string `json:"systemPrompt"`
	SessionID       string `json:"sessionId"`
	Text            string `json:"text"`
}

// handleAgentRun is the LV2 bridge to the embedded DSH gateway. LV1 continues
// to use the existing direct/relay chat path.
func (s *Server) handleAgentRun(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w, http.MethodPost)
		return
	}
	if strings.TrimSpace(s.cfg.DSHGatewayURL) == "" {
		writeAPIError(w, http.StatusServiceUnavailable, "agent_disabled", "DSH agent gateway is not configured")
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if user.Level < 2 {
		writeAPIError(w, http.StatusForbidden, "level_required", "DSH Agent 内核需要 LV2")
		return
	}
	var req AgentRunRequest
	if e := decodeJSONBody(w, r, &req, maxConversationBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	req.Text = strings.TrimSpace(req.Text)
	if req.Text == "" {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "text is required")
		return
	}
	if strings.TrimSpace(req.ProviderID) == "" {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "providerId is required")
		return
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
	payload := gatewayRunRequest{
		BaseURL:         provider.BaseURL,
		APIKey:          provider.APIKey,
		Model:           model,
		ReasoningEffort: req.ReasoningEffort,
		SystemPrompt:    provider.SystemPrompt,
		SessionID:       req.ConversationID,
		Text:            req.Text,
	}
	raw, err := json.Marshal(payload)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), 15*time.Minute)
	defer cancel()
	gatewayURL := strings.TrimRight(s.cfg.DSHGatewayURL, "/") + "/v1/run"
	gatewayReq, err := http.NewRequestWithContext(ctx, http.MethodPost, gatewayURL, bytes.NewReader(raw))
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	gatewayReq.Header.Set("Content-Type", "application/json")
	gatewayReq.Header.Set("Accept", "text/event-stream")
	resp, err := (&http.Client{Timeout: 0}).Do(gatewayReq)
	if err != nil {
		writeAPIError(w, http.StatusBadGateway, "agent_unavailable", "DSH agent gateway unavailable")
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 16<<10))
		var envelope struct {
			Error string `json:"error"`
		}
		_ = json.Unmarshal(body, &envelope)
		message := envelope.Error
		if message == "" {
			message = "DSH agent gateway error"
		}
		writeAPIError(w, http.StatusBadGateway, "agent_error", message)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	flusher, _ := w.(http.Flusher)
	buffer := make([]byte, 32<<10)
	for {
		n, readErr := resp.Body.Read(buffer)
		if n > 0 {
			if _, writeErr := w.Write(buffer[:n]); writeErr != nil {
				return
			}
			if flusher != nil {
				flusher.Flush()
			}
		}
		if readErr != nil {
			return
		}
	}
}
