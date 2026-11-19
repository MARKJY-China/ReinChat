package main

import (
	"bufio"
	"bytes"
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
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
	Type      string           `json:"type"`
	Seq       int64            `json:"seq,omitempty"`
	Text      string           `json:"text,omitempty"`
	Reasoning string           `json:"reasoning,omitempty"`
	Status    string           `json:"status,omitempty"`
	Error     string           `json:"error,omitempty"`
	Tool      string           `json:"tool,omitempty"`
	CallID    string           `json:"callId,omitempty"`
	Query     string           `json:"query,omitempty"`
	URL       string           `json:"url,omitempty"`
	Summary   string           `json:"summary,omitempty"`
	Detail    string           `json:"detail,omitempty"`
	Sources   []SourceDTO      `json:"sources,omitempty"`
	Segments  []MessageSegment `json:"segments,omitempty"`
	Tools     []relayEvent     `json:"tools,omitempty"`
}

const (
	maxRelaySources       = 40
	maxRelayToolDetail    = 4000
	maxRelaySourceSnippet = 400
)

// relayHub keeps the per-job event stream plus the chronological segment
// timeline that a reconnecting client needs to rebuild the process view.
type relayHub struct {
	mu        sync.Mutex
	subs      map[string]map[chan relayEvent]struct{}
	cancels   map[string]context.CancelFunc
	tools     map[string][]relayEvent
	segments  map[string][]MessageSegment
	sources   map[string][]SourceDTO
	seq       map[string]int64
	text      map[string]string
	reasoning map[string]string
}

func newRelayHub() *relayHub {
	return &relayHub{
		subs:      make(map[string]map[chan relayEvent]struct{}),
		cancels:   make(map[string]context.CancelFunc),
		tools:     make(map[string][]relayEvent),
		segments:  make(map[string][]MessageSegment),
		sources:   make(map[string][]SourceDTO),
		seq:       make(map[string]int64),
		text:      make(map[string]string),
		reasoning: make(map[string]string),
	}
}

func (h *relayHub) subscribe(jobID string) (<-chan relayEvent, func()) {
	ch := make(chan relayEvent, 64)
	h.mu.Lock()
	h.addSubscriberLocked(jobID, ch)
	h.mu.Unlock()
	return ch, func() {
		h.mu.Lock()
		h.removeSubscriberLocked(jobID, ch)
		h.mu.Unlock()
	}
}

func (h *relayHub) addSubscriberLocked(jobID string, ch chan relayEvent) {
	if h.subs[jobID] == nil {
		h.subs[jobID] = make(map[chan relayEvent]struct{})
	}
	h.subs[jobID][ch] = struct{}{}
}

func (h *relayHub) removeSubscriberLocked(jobID string, ch chan relayEvent) {
	if set := h.subs[jobID]; set != nil {
		if _, ok := set[ch]; ok {
			delete(set, ch)
			close(ch)
		}
		if len(set) == 0 {
			delete(h.subs, jobID)
		}
	}
}

// snapshotAndSubscribe captures the timeline and attaches a live subscriber
// under the same lock, so no event can fall between the snapshot and the
// stream. Events delivered on the channel all carry Seq > snapshot.Seq.
func (h *relayHub) snapshotAndSubscribe(jobID string) (relayArtifacts, <-chan relayEvent, func()) {
	ch := make(chan relayEvent, 64)
	h.mu.Lock()
	h.addSubscriberLocked(jobID, ch)
	art := relayArtifacts{
		Tools:     append([]relayEvent(nil), h.tools[jobID]...),
		Segments:  append([]MessageSegment(nil), h.segments[jobID]...),
		Sources:   append([]SourceDTO(nil), h.sources[jobID]...),
		Seq:       h.seq[jobID],
		Text:      h.text[jobID],
		Reasoning: h.reasoning[jobID],
	}
	h.mu.Unlock()
	return art, ch, func() {
		h.mu.Lock()
		h.removeSubscriberLocked(jobID, ch)
		h.mu.Unlock()
	}
}

func (h *relayHub) publish(jobID string, event relayEvent) {
	h.mu.Lock()
	defer h.mu.Unlock()
	h.seq[jobID]++
	event.Seq = h.seq[jobID]
	switch event.Type {
	case "tool":
		h.tools[jobID] = append(h.tools[jobID], event)
		h.applyToolSegmentLocked(jobID, event)
		if len(event.Sources) > 0 {
			h.mergeSourcesLocked(jobID, event.Sources)
		}
	case "sources":
		h.mergeSourcesLocked(jobID, event.Sources)
	case "reasoning":
		h.reasoning[jobID] += event.Text
		h.appendReasoningLocked(jobID, event.Text)
	case "delta":
		h.text[jobID] += event.Text
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
	delete(h.segments, jobID)
	delete(h.sources, jobID)
	delete(h.seq, jobID)
	delete(h.text, jobID)
	delete(h.reasoning, jobID)
	h.mu.Unlock()
}

func (h *relayHub) toolHistory(jobID string) []relayEvent {
	h.mu.Lock()
	defer h.mu.Unlock()
	return append([]relayEvent(nil), h.tools[jobID]...)
}

func (h *relayHub) segmentHistory(jobID string) []MessageSegment {
	h.mu.Lock()
	defer h.mu.Unlock()
	return append([]MessageSegment(nil), h.segments[jobID]...)
}

func (h *relayHub) sourceHistory(jobID string) []SourceDTO {
	h.mu.Lock()
	defer h.mu.Unlock()
	return append([]SourceDTO(nil), h.sources[jobID]...)
}

func (h *relayHub) artifactSnapshot(jobID string) relayArtifacts {
	h.mu.Lock()
	defer h.mu.Unlock()
	return relayArtifacts{
		Tools:     append([]relayEvent(nil), h.tools[jobID]...),
		Segments:  append([]MessageSegment(nil), h.segments[jobID]...),
		Sources:   append([]SourceDTO(nil), h.sources[jobID]...),
		Seq:       h.seq[jobID],
		Text:      h.text[jobID],
		Reasoning: h.reasoning[jobID],
	}
}

func (h *relayHub) appendReasoningLocked(jobID, text string) {
	if text == "" {
		return
	}
	segments := h.segments[jobID]
	if n := len(segments); n > 0 && segments[n-1].Type == "reasoning" {
		segments[n-1].Text += text
	} else {
		segments = append(segments, MessageSegment{Type: "reasoning", Text: text})
	}
	h.segments[jobID] = segments
}

func (h *relayHub) applyToolSegmentLocked(jobID string, event relayEvent) {
	segments := h.segments[jobID]
	index := -1
	for i := len(segments) - 1; i >= 0; i-- {
		segment := segments[i]
		if segment.Type != "tool" {
			continue
		}
		if event.CallID != "" && segment.CallID == event.CallID {
			index = i
			break
		}
		if event.CallID == "" && segment.Name == event.Tool && segment.Status == "running" {
			index = i
			break
		}
	}
	if index < 0 {
		segments = append(segments, newToolSegment(event))
		h.segments[jobID] = segments
		return
	}
	segment := segments[index]
	segment.Status = event.Status
	segment.Label = toolSegmentLabel(event.Tool, event.Status)
	if event.CallID != "" {
		segment.CallID = event.CallID
	}
	if event.Query != "" {
		segment.Query = truncateRunes(event.Query, 500)
	}
	if event.URL != "" {
		segment.URL = truncateRunes(event.URL, 2048)
	}
	if event.Summary != "" {
		segment.Summary = truncateRunes(event.Summary, 500)
	}
	if event.Status != "running" && event.Detail != "" {
		segment.Detail = truncateRunes(event.Detail, maxRelayToolDetail)
	}
	segments[index] = segment
	h.segments[jobID] = segments
}

func newToolSegment(event relayEvent) MessageSegment {
	segment := MessageSegment{
		Type:   "tool",
		Name:   event.Tool,
		CallID: event.CallID,
		Label:  toolSegmentLabel(event.Tool, event.Status),
		Status: event.Status,
	}
	if segment.Status == "" {
		segment.Status = "running"
	}
	if event.Query != "" {
		segment.Query = truncateRunes(event.Query, 500)
	}
	if event.URL != "" {
		segment.URL = truncateRunes(event.URL, 2048)
	}
	if event.Summary != "" {
		segment.Summary = truncateRunes(event.Summary, 500)
	}
	if event.Status != "running" && event.Detail != "" {
		segment.Detail = truncateRunes(event.Detail, maxRelayToolDetail)
	}
	return segment
}

func toolSegmentLabel(name, status string) string {
	searching := status == "" || status == "running"
	switch name {
	case "web_search":
		if searching {
			return "正在搜索网页"
		}
		return "搜索网页"
	case "fetch_url":
		if searching {
			return "正在浏览网页"
		}
		return "浏览网页"
	default:
		if searching {
			return "正在使用工具"
		}
		return "使用工具"
	}
}

// addSources registers search results for a job, de-duplicates them by URL and
// assigns stable [n] indexes. It returns the full list and the newly added
// entries (whose indexes the model must use in the tool result).
func (h *relayHub) addSources(jobID string, results []webResult) (all []SourceDTO, added []SourceDTO) {
	h.mu.Lock()
	defer h.mu.Unlock()
	list := h.sources[jobID]
	seen := make(map[string]struct{}, len(list))
	for _, source := range list {
		seen[source.URL] = struct{}{}
	}
	for _, result := range results {
		rawURL := strings.TrimSpace(result.URL)
		if rawURL == "" || (!strings.HasPrefix(rawURL, "http://") && !strings.HasPrefix(rawURL, "https://")) {
			continue
		}
		if _, ok := seen[rawURL]; ok {
			continue
		}
		if len(list) >= maxRelaySources {
			break
		}
		title := strings.TrimSpace(result.Title)
		if title == "" {
			title = rawURL
		}
		source := SourceDTO{
			Index:   len(list) + 1,
			Title:   truncateRunes(title, 300),
			URL:     truncateRunes(rawURL, 2048),
			Snippet: truncateRunes(strings.TrimSpace(result.Snippet), maxRelaySourceSnippet),
		}
		list = append(list, source)
		added = append(added, source)
		seen[rawURL] = struct{}{}
	}
	h.sources[jobID] = list
	all = append([]SourceDTO(nil), list...)
	return all, added
}

func (h *relayHub) mergeSourcesLocked(jobID string, incoming []SourceDTO) {
	list := h.sources[jobID]
	seen := make(map[string]struct{}, len(list))
	for _, source := range list {
		seen[source.URL] = struct{}{}
	}
	for _, source := range incoming {
		if source.URL == "" || (!strings.HasPrefix(source.URL, "http://") && !strings.HasPrefix(source.URL, "https://")) {
			continue
		}
		if _, ok := seen[source.URL]; ok {
			continue
		}
		if len(list) >= maxRelaySources {
			break
		}
		source.Index = len(list) + 1
		list = append(list, source)
		seen[source.URL] = struct{}{}
	}
	h.sources[jobID] = list
}

// relayArtifacts is the persisted process timeline of a relay job. It lets a
// reconnecting client recover the full tool history even after the job ended
// or the process restarted.
type relayArtifacts struct {
	Tools    []relayEvent     `json:"tools"`
	Segments []MessageSegment `json:"segments"`
	Sources  []SourceDTO      `json:"sources"`
	Seq      int64            `json:"seq"`
	// Text/Reasoning are only used for live snapshots; relay_jobs keeps the
	// authoritative copy of the final answer.
	Text      string `json:"-"`
	Reasoning string `json:"-"`
}

func (a relayArtifacts) empty() bool {
	return len(a.Tools) == 0 && len(a.Segments) == 0 && len(a.Sources) == 0 &&
		a.Text == "" && a.Reasoning == ""
}

func (s *Server) saveRelayArtifacts(jobID string) {
	artifacts := s.relay.artifactSnapshot(jobID)
	raw, err := json.Marshal(artifacts)
	if err != nil {
		s.logger.Printf("encode relay artifacts: %v", err)
		return
	}
	if _, err := s.db.Exec(`UPDATE relay_jobs SET artifacts_json = ? WHERE id = ?`, string(raw), jobID); err != nil {
		s.logger.Printf("persist relay artifacts: %v", err)
	}
}

func (s *Server) loadRelayArtifacts(ctx context.Context, jobID string) relayArtifacts {
	var raw string
	if err := s.db.QueryRowContext(ctx,
		`SELECT artifacts_json FROM relay_jobs WHERE id = ?`, jobID).Scan(&raw); err != nil {
		return relayArtifacts{}
	}
	var artifacts relayArtifacts
	if strings.TrimSpace(raw) == "" || raw == "{}" {
		return relayArtifacts{}
	}
	if err := json.Unmarshal([]byte(raw), &artifacts); err != nil {
		return relayArtifacts{}
	}
	return artifacts
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
	terminal := job.Status == "completed" || job.Status == "failed" || job.Status == "cancelled"
	var artifacts relayArtifacts
	var ch <-chan relayEvent
	var cancel func()
	if !terminal {
		artifacts, ch, cancel = s.relay.snapshotAndSubscribe(jobID)
		defer cancel()
	}
	if artifacts.empty() {
		// The live hub is authoritative while a job runs. Once it ends (or
		// after a restart) fall back to the persisted timeline.
		artifacts = s.loadRelayArtifacts(r.Context(), jobID)
	}
	if artifacts.empty() {
		artifacts = s.relay.artifactSnapshot(jobID)
	}
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	text, reasoning := job.Text, job.Reasoning
	if artifacts.Text != "" {
		text = artifacts.Text
	}
	if artifacts.Reasoning != "" {
		reasoning = artifacts.Reasoning
	}
	snapshot, _ := json.Marshal(relayEvent{
		Type: "snapshot", Text: text, Reasoning: reasoning, Status: job.Status, Error: job.Error,
		Tools: artifacts.Tools, Segments: artifacts.Segments, Sources: artifacts.Sources, Seq: artifacts.Seq,
	})
	_, _ = fmt.Fprintf(w, "event: snapshot\ndata: %s\n\n", snapshot)
	flusher.Flush()
	if terminal {
		return
	}
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
			if event.Seq > 0 && event.Seq <= artifacts.Seq {
				continue
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
	defer func() {
		// Persist tools/segments/sources before dropping the live hub state so a
		// reconnecting client can still recover the whole process timeline.
		s.saveRelayArtifacts(jobID)
		s.relay.removeCancel(jobID)
	}()
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
		if query := lastUserQuery(messages); query != "" {
			if contextText, ok := s.webResearch(ctx, jobID, query); ok {
				if instruction := s.citationInstruction(jobID); instruction != "" {
					contextText += "\n\n" + instruction
				}
				messages = append([]MessageDTO{{
					ID: "web-research", Role: "system", Content: contextText,
				}}, messages...)
				s.relay.publish(jobID, relayEvent{Type: "status", Status: "searching"})
			}
		}
	}
	req, err := s.buildRelayRequest(ctx, p, model, effort, messages)
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

func (s *Server) buildRelayRequest(ctx context.Context, p *relayProvider, model, effort string, messages []MessageDTO) (*http.Request, error) {
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
		content := m.Content
		if strings.TrimSpace(m.PDFText) != "" {
			content += "\n\n[附件 PDF：" + m.PDFName + "]\n" + m.PDFText
		}
		parts, err := s.imageContentParts(m.Images)
		if err != nil {
			return nil, err
		}
		if len(parts) > 0 {
			multimodal := []any{map[string]any{"type": "text", "text": content}}
			for _, part := range parts {
				multimodal = append(multimodal, part)
			}
			list = append(list, map[string]any{"role": m.Role, "content": multimodal})
		} else {
			list = append(list, map[string]any{"role": m.Role, "content": content})
		}
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

func (s *Server) imageContentParts(images []ImageDTO) ([]any, error) {
	parts := make([]any, 0, len(images))
	for _, image := range images {
		if image.ID == "" {
			continue
		}
		raw, err := os.ReadFile(filepath.Join(s.filesDir, image.ID))
		if err != nil {
			return nil, errors.New("无法读取附件图片")
		}
		mimeType := image.MimeType
		if mimeType == "" {
			mimeType = "image/png"
		}
		parts = append(parts, map[string]any{
			"type": "image_url",
			"image_url": map[string]any{
				"url": "data:" + mimeType + ";base64," + base64.StdEncoding.EncodeToString(raw),
			},
		})
	}
	return parts, nil
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
