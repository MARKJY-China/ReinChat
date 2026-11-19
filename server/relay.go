package main

import (
	"bufio"
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync"
	"time"
)

type RelayJobDTO struct {
	ID             string `json:"id"`
	ConversationID string `json:"conversationId,omitempty"`
	ProviderID     string `json:"providerId"`
	Status         string `json:"status"`
	Text           string `json:"text"`
	Reasoning      string `json:"reasoning"`
	Error          string `json:"error,omitempty"`
	CreatedAt      int64  `json:"createdAt"`
	UpdatedAt      int64  `json:"updatedAt"`
}

type RelayCreateRequest struct {
	ProviderID      string       `json:"providerId"`
	ConversationID  string       `json:"conversationId"`
	Model           string       `json:"model"`
	ReasoningEffort string       `json:"reasoningEffort"`
	Search          bool         `json:"search"`
	Messages        []MessageDTO `json:"messages"`
}

type relayEvent struct {
	Type      string       `json:"type"`
	Text      string       `json:"text,omitempty"`
	Reasoning string       `json:"reasoning,omitempty"`
	Status    string       `json:"status,omitempty"`
	Error     string       `json:"error,omitempty"`
	Tool      string       `json:"tool,omitempty"`
	Detail    string       `json:"detail,omitempty"`
	Tools     []relayEvent `json:"tools,omitempty"`
}

type relayHub struct {
	mu      sync.Mutex
	subs    map[string]map[chan relayEvent]struct{}
	cancels map[string]context.CancelFunc
	tools   map[string][]relayEvent
}

func newRelayHub() *relayHub {
	return &relayHub{
		subs:    make(map[string]map[chan relayEvent]struct{}),
		cancels: make(map[string]context.CancelFunc),
		tools:   make(map[string][]relayEvent),
	}
}

func (h *relayHub) subscribe(jobID string) (<-chan relayEvent, func()) {
	ch := make(chan relayEvent, 64)
	h.mu.Lock()
	if h.subs[jobID] == nil {
		h.subs[jobID] = make(map[chan relayEvent]struct{})
	}
	h.subs[jobID][ch] = struct{}{}
	h.mu.Unlock()
	return ch, func() {
		h.mu.Lock()
		if set := h.subs[jobID]; set != nil {
			if _, ok := set[ch]; ok {
				delete(set, ch)
				close(ch)
			}
			if len(set) == 0 {
				delete(h.subs, jobID)
			}
		}
		h.mu.Unlock()
	}
}

func (h *relayHub) publish(jobID string, event relayEvent) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if event.Type == "tool" {
		h.tools[jobID] = append(h.tools[jobID], event)
	}
	for ch := range h.subs[jobID] {
		select {
		case ch <- event:
		default:
		}
	}
}

func (h *relayHub) registerCancel(jobID string, cancel context.CancelFunc) {
	h.mu.Lock()
	h.cancels[jobID] = cancel
	h.mu.Unlock()
}

func (h *relayHub) cancel(jobID string) bool {
	h.mu.Lock()
	cancel, ok := h.cancels[jobID]
	h.mu.Unlock()
	if ok {
		cancel()
	}
	return ok
}

func (h *relayHub) removeCancel(jobID string) {
	h.mu.Lock()
	delete(h.cancels, jobID)
	delete(h.tools, jobID)
	h.mu.Unlock()
}

func (h *relayHub) toolHistory(jobID string) []relayEvent {
	h.mu.Lock()
	defer h.mu.Unlock()
	return append([]relayEvent(nil), h.tools[jobID]...)
}

type RelayProviderDTO struct {
	ID       string          `json:"id"`
	Name     string          `json:"name"`
	Protocol string          `json:"protocol"`
	Model    string          `json:"model"`
	Models   []ProviderModel `json:"models,omitempty"`
}

func (s *Server) handleRelayProviders(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	rows, err := s.db.QueryContext(r.Context(),
		`SELECT id, name, protocol, model FROM providers WHERE user_id = ? ORDER BY name COLLATE NOCASE, id`, user.ID)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer rows.Close()
	items := make([]RelayProviderDTO, 0)
	for rows.Next() {
		var item RelayProviderDTO
		if err := rows.Scan(&item.ID, &item.Name, &item.Protocol, &item.Model); err != nil {
			s.internalError(w, r, err)
			return
		}
		item.Models = decodeProviderModels(item.Model)
		item.Model = modelIDs(item.Models)
		items = append(items, item)
	}
	writeJSON(w, http.StatusOK, map[string]any{"providers": items})
}

func (s *Server) handleRelayCreate(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w, http.MethodPost)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	var req RelayCreateRequest
	if e := decodeJSONBody(w, r, &req, maxConversationBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	if strings.TrimSpace(req.ProviderID) == "" {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "providerId is required")
		return
	}
	if len(req.Messages) == 0 {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "messages is required")
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
	} else {
		allowed := false
		for _, candidate := range strings.Split(provider.Model, ",") {
			if strings.TrimSpace(candidate) == model {
				allowed = true
				break
			}
		}
		if !allowed {
			writeAPIError(w, http.StatusBadRequest, "invalid_model", "model is not configured for this provider")
			return
		}
	}
	effort := strings.ToLower(strings.TrimSpace(req.ReasoningEffort))
	if effort != "" && effort != "low" && effort != "high" && effort != "max" && effort != "medium" {
		writeAPIError(w, http.StatusBadRequest, "invalid_effort", "reasoningEffort must be low, medium, high, or max")
		return
	}
	id, err := newUUID()
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	now := s.now().UnixMilli()
	if _, err := s.db.ExecContext(r.Context(),
		`INSERT INTO relay_jobs
		 (id, user_id, conversation_id, provider_id, status, text, reasoning, created_at, updated_at)
		 VALUES (?, ?, ?, ?, 'queued', '', '', ?, ?)`,
		id, user.ID, req.ConversationID, provider.ID, now, now); err != nil {
		s.internalError(w, r, err)
		return
	}
	ctx, cancel := context.WithCancel(context.Background())
	s.relay.registerCancel(id, cancel)
	go s.runRelayJob(ctx, user.ID, id, provider, model, effort, req.Search, req.Messages)
	job, err := s.loadRelayJob(r.Context(), user.ID, id)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusAccepted, job)
}

func (s *Server) handleRelayJobItem(w http.ResponseWriter, r *http.Request, rest string) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	parts := strings.Split(strings.Trim(rest, "/"), "/")
	if len(parts) == 0 || !isUUID(parts[0]) {
		writeAPIError(w, http.StatusNotFound, "not_found", "job not found")
		return
	}
	jobID := parts[0]
	if len(parts) == 2 && parts[1] == "events" {
		s.handleRelayEvents(w, r, user, jobID)
		return
	}
	if len(parts) == 2 && parts[1] == "cancel" && r.Method == http.MethodPost {
		if _, err := s.loadRelayJob(r.Context(), user.ID, jobID); errors.Is(err, sql.ErrNoRows) {
			writeAPIError(w, http.StatusNotFound, "not_found", "job not found")
			return
		} else if err != nil {
			s.internalError(w, r, err)
			return
		}
		if !s.relay.cancel(jobID) {
			writeAPIError(w, http.StatusConflict, "not_running", "job is not running")
			return
		}
		writeNoContent(w)
		return
	}
	if len(parts) != 1 {
		writeAPIError(w, http.StatusNotFound, "not_found", "job endpoint not found")
		return
	}
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	job, err := s.loadRelayJob(r.Context(), user.ID, jobID)
	if errors.Is(err, sql.ErrNoRows) {
		writeAPIError(w, http.StatusNotFound, "not_found", "job not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, job)
}

func (s *Server) handleRelayEvents(w http.ResponseWriter, r *http.Request, user *authUser, jobID string) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	flusher, ok := w.(http.Flusher)
	if !ok {
		writeAPIError(w, http.StatusInternalServerError, "stream_unsupported", "streaming is not supported")
		return
	}
	job, err := s.loadRelayJob(r.Context(), user.ID, jobID)
	if errors.Is(err, sql.ErrNoRows) {
		writeAPIError(w, http.StatusNotFound, "not_found", "job not found")
		return
	}
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	snapshot, _ := json.Marshal(relayEvent{
		Type: "snapshot", Text: job.Text, Reasoning: job.Reasoning, Status: job.Status, Error: job.Error,
		Tools: s.relay.toolHistory(jobID),
	})
	_, _ = fmt.Fprintf(w, "event: snapshot\ndata: %s\n\n", snapshot)
	flusher.Flush()
	if job.Status == "completed" || job.Status == "failed" || job.Status == "cancelled" {
		return
	}
	ch, cancel := s.relay.subscribe(jobID)
	defer cancel()
	ticker := time.NewTicker(20 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case <-ticker.C:
			_, _ = fmt.Fprint(w, ": ping\n\n")
			flusher.Flush()
		case event, open := <-ch:
			if !open {
				return
			}
			payload, _ := json.Marshal(event)
			if _, err := fmt.Fprintf(w, "event: relay\ndata: %s\n\n", payload); err != nil {
				return
			}
			flusher.Flush()
		}
	}
}

type relayProvider struct {
	ID, Protocol, BaseURL, APIKey, Model, SystemPrompt string
	Models                                             []ProviderModel
	Temperature                                        *float64
	MaxTokens                                          *int
}

func (s *Server) loadRelayProvider(ctx context.Context, userID int64, providerID string) (*relayProvider, error) {
	var p relayProvider
	var encrypted []byte
	var temp sql.NullFloat64
	var max sql.NullInt64
	err := s.db.QueryRowContext(ctx,
		`SELECT id, protocol, base_url, api_key_enc, model, system_prompt, temperature, max_tokens
		 FROM providers WHERE user_id = ? AND id = ?`, userID, providerID).
		Scan(&p.ID, &p.Protocol, &p.BaseURL, &encrypted, &p.Model, &p.SystemPrompt, &temp, &max)
	if err != nil {
		return nil, err
	}
	key, err := s.aead.open(encrypted, providerAAD(userID, providerID))
	if err != nil {
		return nil, err
	}
	p.APIKey = string(key)
	p.Models = decodeProviderModels(p.Model)
	p.Model = modelIDs(p.Models)
	if temp.Valid {
		v := temp.Float64
		p.Temperature = &v
	}
	if max.Valid {
		v := int(max.Int64)
		p.MaxTokens = &v
	}
	return &p, nil
}

func (s *Server) runRelayJob(ctx context.Context, userID int64, jobID string, p *relayProvider, model, effort string, search bool, messages []MessageDTO) {
	defer s.relay.removeCancel(jobID)
	fail := func(code, message string) {
		s.updateRelayJob(jobID, "failed", nil, nil, message)
		s.relay.publish(jobID, relayEvent{Type: "error", Status: "failed", Error: message})
	}
	_ = s.updateRelayJob(jobID, "running", nil, nil, "")
	if search {
		if p.Protocol != "RESPONSES" {
			if s.runRelaySearchAgent(ctx, jobID, p, model, effort, messages) {
				return
			}
		}
		if contextText := s.webResearch(ctx, messages); contextText != "" {
			messages = append([]MessageDTO{{
				ID: "web-research", Role: "system", Content: contextText,
			}}, messages...)
			s.relay.publish(jobID, relayEvent{Type: "status", Status: "searching"})
		}
	}
	req, err := buildRelayRequest(ctx, p, model, effort, messages)
	if err != nil {
		fail("request_error", err.Error())
		return
	}
	client := &http.Client{Timeout: 0}
	resp, err := client.Do(req)
	if err != nil {
		fail("upstream_error", "连接服务商失败")
		return
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(io.LimitReader(resp.Body, 16<<10))
		fail("upstream_error", upstreamError(resp.StatusCode, raw))
		return
	}
	scanner := bufio.NewScanner(resp.Body)
	scanner.Buffer(make([]byte, 64<<10), 2<<20)
	var text, reasoning strings.Builder
	lastSave := time.Now()
	finished := false
	for scanner.Scan() {
		if ctx.Err() != nil {
			textNow, reasoningNow := text.String(), reasoning.String()
			_ = s.updateRelayJob(jobID, "cancelled", &textNow, &reasoningNow, "")
			s.relay.publish(jobID, relayEvent{Type: "status", Status: "cancelled"})
			return
		}
		line := strings.TrimSpace(scanner.Text())
		if !strings.HasPrefix(line, "data:") {
			continue
		}
		data := strings.TrimSpace(strings.TrimPrefix(line, "data:"))
		if data == "" || data == "[DONE]" {
			if data == "[DONE]" {
				finished = true
				break
			}
			continue
		}
		deltaText, deltaReasoning, done := parseRelayDelta(p.Protocol, data)
		if deltaText != "" {
			text.WriteString(deltaText)
			s.relay.publish(jobID, relayEvent{Type: "delta", Text: deltaText})
		}
		if deltaReasoning != "" {
			reasoning.WriteString(deltaReasoning)
			s.relay.publish(jobID, relayEvent{Type: "reasoning", Text: deltaReasoning})
		}
		if done {
			finished = true
		}
		if time.Since(lastSave) > 200*time.Millisecond {
			textNow, reasoningNow := text.String(), reasoning.String()
			_ = s.updateRelayJob(jobID, "running", &textNow, &reasoningNow, "")
			lastSave = time.Now()
		}
		if finished {
			break
		}
	}
	if err := scanner.Err(); err != nil && ctx.Err() == nil {
		fail("stream_error", "读取服务商流失败")
		return
	}
	textNow, reasoningNow := text.String(), reasoning.String()
	_ = s.updateRelayJob(jobID, "completed", &textNow, &reasoningNow, "")
	s.relay.publish(jobID, relayEvent{Type: "status", Status: "completed"})
}

func buildRelayRequest(ctx context.Context, p *relayProvider, model, effort string, messages []MessageDTO) (*http.Request, error) {
	base := strings.TrimRight(strings.TrimSpace(p.BaseURL), "/")
	if base == "" {
		return nil, errors.New("服务商 Base URL 为空")
	}
	chat := map[string]any{
		"model":  model,
		"stream": true,
	}
	list := make([]map[string]any, 0, len(messages)+1)
	if strings.TrimSpace(p.SystemPrompt) != "" {
		list = append(list, map[string]any{"role": "system", "content": p.SystemPrompt})
	}
	for _, m := range messages {
		if m.Role != "user" && m.Role != "assistant" && m.Role != "system" {
			continue
		}
		list = append(list, map[string]any{"role": m.Role, "content": m.Content})
	}
	chat["messages"] = list
	if p.Temperature != nil {
		chat["temperature"] = *p.Temperature
	}
	if p.MaxTokens != nil && *p.MaxTokens > 0 {
		chat["max_tokens"] = *p.MaxTokens
	}
	if effort != "" {
		chat["reasoning_effort"] = effort
	}
	var endpoint string
	var body any
	if p.Protocol == "RESPONSES" {
		endpoint = base + "/responses"
		body = map[string]any{
			"model":        model,
			"stream":       true,
			"instructions": p.SystemPrompt,
			"input":        list,
		}
		if effort != "" {
			body.(map[string]any)["reasoning"] = map[string]any{"effort": effort}
		}
	} else {
		endpoint = base + "/chat/completions"
		body = chat
	}
	raw, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(raw))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "text/event-stream")
	if p.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+p.APIKey)
	}
	return req, nil
}

func parseRelayDelta(protocol, raw string) (text, reasoning string, done bool) {
	var root map[string]any
	if json.Unmarshal([]byte(raw), &root) != nil {
		return "", "", false
	}
	if protocol == "RESPONSES" {
		typ, _ := root["type"].(string)
		switch typ {
		case "response.output_text.delta":
			text, _ = root["delta"].(string)
		case "response.reasoning_summary_text.delta", "response.reasoning_text.delta":
			reasoning, _ = root["delta"].(string)
		case "response.completed", "response.incomplete":
			done = true
		}
		return text, reasoning, done
	}
	choices, _ := root["choices"].([]any)
	if len(choices) == 0 {
		return "", "", false
	}
	choice, _ := choices[0].(map[string]any)
	delta, _ := choice["delta"].(map[string]any)
	if delta != nil {
		text, _ = delta["content"].(string)
		reasoning, _ = delta["reasoning_content"].(string)
		if reasoning == "" {
			reasoning, _ = delta["reasoning"].(string)
		}
	}
	if choice["finish_reason"] != nil {
		done = true
	}
	return text, reasoning, done
}

func upstreamError(status int, raw []byte) string {
	var root map[string]any
	if json.Unmarshal(raw, &root) == nil {
		if e, ok := root["error"].(map[string]any); ok {
			if msg, ok := e["message"].(string); ok && msg != "" {
				return fmt.Sprintf("服务商错误 %d：%s", status, msg)
			}
		}
	}
	return fmt.Sprintf("服务商错误 %d", status)
}

func (s *Server) updateRelayJob(jobID, status string, text, reasoning *string, errText string) error {
	now := s.now().UnixMilli()
	_, err := s.db.Exec(
		`UPDATE relay_jobs SET status = ?, text = COALESCE(?, text), reasoning = COALESCE(?, reasoning),
		 error = ?, updated_at = ? WHERE id = ?`,
		status, text, reasoning, nullIfEmpty(errText), now, jobID)
	return err
}

func nullIfEmpty(s string) any {
	if s == "" {
		return nil
	}
	return s
}

func (s *Server) loadRelayJob(ctx context.Context, userID int64, jobID string) (*RelayJobDTO, error) {
	var job RelayJobDTO
	var errText sql.NullString
	err := s.db.QueryRowContext(ctx,
		`SELECT id, COALESCE(conversation_id, ''), provider_id, status, text, reasoning, error, created_at, updated_at
		 FROM relay_jobs WHERE id = ? AND user_id = ?`, jobID, userID).
		Scan(&job.ID, &job.ConversationID, &job.ProviderID, &job.Status, &job.Text, &job.Reasoning,
			&errText, &job.CreatedAt, &job.UpdatedAt)
	if err != nil {
		return nil, err
	}
	if errText.Valid {
		job.Error = errText.String
	}
	return &job, nil
}
