package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

func testUserID(t *testing.T, env *testEnv, username string) int64 {
	t.Helper()
	var id int64
	if err := env.app.db.QueryRow(
		`SELECT id FROM users WHERE username_lower = lower(?)`, username).Scan(&id); err != nil {
		t.Fatalf("lookup user id: %v", err)
	}
	return id
}

func insertRelayJob(t *testing.T, env *testEnv, userID int64, jobID, conversationID string) {
	t.Helper()
	now := env.app.now().UnixMilli()
	if _, err := env.app.db.Exec(
		`INSERT INTO relay_jobs
		 (id, user_id, conversation_id, provider_id, status, text, reasoning, created_at, updated_at)
		 VALUES (?, ?, ?, 'provider-test', 'running', '', '', ?, ?)`,
		jobID, userID, conversationID, now, now); err != nil {
		t.Fatalf("insert relay job: %v", err)
	}
}

func TestRelayHubSegmentsStayChronological(t *testing.T) {
	hub := newRelayHub()
	const job = "job-1"

	hub.publish(job, relayEvent{Type: "reasoning", Text: "思考A"})
	hub.publish(job, relayEvent{Type: "tool", Tool: "web_search", CallID: "call_1", Status: "running", Query: "reinchat"})
	hub.publish(job, relayEvent{
		Type: "tool", Tool: "web_search", CallID: "call_1", Status: "done", Query: "reinchat",
		Summary: "找到 2 个网页", Detail: "[1] A\nhttps://a.example\n[2] B\nhttps://b.example",
		Sources: []SourceDTO{
			{Index: 1, Title: "A", URL: "https://a.example"},
			{Index: 2, Title: "B", URL: "https://b.example"},
		},
	})
	hub.publish(job, relayEvent{Type: "reasoning", Text: "思考B"})
	hub.publish(job, relayEvent{Type: "tool", Tool: "fetch_url", CallID: "call_2", Status: "running", URL: "https://a.example"})
	hub.publish(job, relayEvent{
		Type: "tool", Tool: "fetch_url", CallID: "call_2", Status: "done", URL: "https://a.example",
		Summary: "读取 120 字", Detail: "页面正文",
	})
	hub.publish(job, relayEvent{Type: "reasoning", Text: "思考C"})

	segments := hub.segmentHistory(job)
	wantTypes := []string{"reasoning", "tool", "reasoning", "tool", "reasoning"}
	if len(segments) != len(wantTypes) {
		t.Fatalf("segments = %#v, want %d entries", segments, len(wantTypes))
	}
	for i, want := range wantTypes {
		if segments[i].Type != want {
			t.Fatalf("segment %d type = %q, want %q (%#v)", i, segments[i].Type, want, segments)
		}
	}
	if segments[0].Text != "思考A" || segments[2].Text != "思考B" || segments[4].Text != "思考C" {
		t.Fatalf("reasoning order lost: %#v", segments)
	}
	search := segments[1]
	if search.Status != "done" || search.Query != "reinchat" || search.Summary != "找到 2 个网页" || search.CallID != "call_1" {
		t.Fatalf("search segment = %#v", search)
	}
	browse := segments[3]
	if browse.Status != "done" || browse.URL != "https://a.example" || browse.Summary != "读取 120 字" {
		t.Fatalf("browse segment = %#v", browse)
	}
	if search.Label != "搜索网页" || browse.Label != "浏览网页" {
		t.Fatalf("labels = %q %q", search.Label, browse.Label)
	}

	tools := hub.toolHistory(job)
	if len(tools) != 4 || tools[0].Status != "running" || tools[1].Status != "done" {
		t.Fatalf("tool history = %#v", tools)
	}
	for i := 1; i < len(tools); i++ {
		if tools[i].Seq <= tools[i-1].Seq {
			t.Fatalf("tool seq not increasing: %d then %d", tools[i-1].Seq, tools[i].Seq)
		}
	}
	sources := hub.sourceHistory(job)
	if len(sources) != 2 || sources[0].Index != 1 || sources[1].Index != 2 {
		t.Fatalf("sources = %#v", sources)
	}
}

func TestRelaySnapshotCarriesTimelineOnReconnect(t *testing.T) {
	env := newTestEnv(t)
	auth := env.register("timeline_user", "timeline password")
	userID := testUserID(t, env, auth.User.Username)
	jobID := "aaaaaaaa-1111-4111-8111-111111111111"
	insertRelayJob(t, env, userID, jobID, "")

	env.app.relay.publish(jobID, relayEvent{Type: "reasoning", Text: "思考A"})
	env.app.relay.publish(jobID, relayEvent{Type: "tool", Tool: "web_search", CallID: "call_1", Status: "running", Query: "reinchat"})
	env.app.relay.publish(jobID, relayEvent{
		Type: "tool", Tool: "web_search", CallID: "call_1", Status: "done", Query: "reinchat",
		Summary: "找到 1 个网页", Detail: "[1] ReinChat\nhttps://example.com/reinchat",
		Sources: []SourceDTO{{Index: 1, Title: "ReinChat", URL: "https://example.com/reinchat"}},
	})
	env.app.relay.publish(jobID, relayEvent{Type: "reasoning", Text: "思考B"})
	env.app.relay.publish(jobID, relayEvent{Type: "delta", Text: "答案[1]"})

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		env.server.URL+"/api/v1/relay/jobs/"+jobID+"/events", nil)
	if err != nil {
		t.Fatalf("create request: %v", err)
	}
	req.Header.Set("Authorization", "Bearer "+auth.Token)
	resp, err := env.client.Do(req)
	if err != nil {
		t.Fatalf("open events: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("events status = %d", resp.StatusCode)
	}
	reader := bufio.NewReader(resp.Body)
	snapshot := readSSEData(t, reader, "snapshot")
	var event relayEvent
	if err := json.Unmarshal([]byte(snapshot), &event); err != nil {
		t.Fatalf("decode snapshot %q: %v", snapshot, err)
	}
	if event.Type != "snapshot" || event.Seq == 0 {
		t.Fatalf("snapshot = %#v", event)
	}
	if event.Text != "答案[1]" || event.Reasoning != "思考A思考B" {
		t.Fatalf("snapshot text/reasoning = %q / %q", event.Text, event.Reasoning)
	}
	if len(event.Tools) != 2 || event.Tools[0].Status != "running" || event.Tools[1].Status != "done" {
		t.Fatalf("snapshot tools = %#v", event.Tools)
	}
	if event.Tools[1].Summary != "找到 1 个网页" || event.Tools[1].Query != "reinchat" {
		t.Fatalf("snapshot tool summary/query = %#v", event.Tools[1])
	}
	if len(event.Segments) != 3 ||
		event.Segments[0].Type != "reasoning" ||
		event.Segments[1].Type != "tool" || event.Segments[1].Status != "done" ||
		event.Segments[2].Type != "reasoning" {
		t.Fatalf("snapshot segments = %#v", event.Segments)
	}
	if len(event.Sources) != 1 || event.Sources[0].URL != "https://example.com/reinchat" {
		t.Fatalf("snapshot sources = %#v", event.Sources)
	}

	// Events published after the snapshot must all arrive with a higher seq,
	// proving the handler subscribes before it writes the snapshot.
	env.app.relay.publish(jobID, relayEvent{Type: "tool", Tool: "fetch_url", CallID: "call_2", Status: "running", URL: "https://example.com/reinchat"})
	live := readSSEData(t, reader, "relay")
	var liveEvent relayEvent
	if err := json.Unmarshal([]byte(live), &liveEvent); err != nil {
		t.Fatalf("decode live event %q: %v", live, err)
	}
	if liveEvent.Type != "tool" || liveEvent.Seq <= event.Seq {
		t.Fatalf("live event = %#v (snapshot seq %d)", liveEvent, event.Seq)
	}
}

// readSSEData reads the next SSE block whose event name matches want and
// returns its data payload.
func readSSEData(t *testing.T, reader *bufio.Reader, want string) string {
	t.Helper()
	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		name := ""
		data := ""
		for {
			line, err := reader.ReadString('\n')
			if err != nil {
				t.Fatalf("read SSE while waiting for %q: %v", want, err)
			}
			line = strings.TrimRight(line, "\r\n")
			if line == "" {
				break
			}
			if strings.HasPrefix(line, "event:") {
				name = strings.TrimSpace(strings.TrimPrefix(line, "event:"))
			}
			if strings.HasPrefix(line, "data:") {
				data += strings.TrimSpace(strings.TrimPrefix(line, "data:"))
			}
		}
		if name == want && data != "" {
			return data
		}
	}
	t.Fatalf("timed out waiting for SSE event %q", want)
	return ""
}

func TestAgentToolEmitsRunningDoneAndSources(t *testing.T) {
	env := newTestEnv(t)
	auth := env.register("tool_user", "tool password")
	userID := testUserID(t, env, auth.User.Username)
	jobID := "bbbbbbbb-1111-4111-8111-111111111111"
	insertRelayJob(t, env, userID, jobID, "")

	env.app.webSearch = func(ctx context.Context, query string) []webResult {
		return []webResult{
			{Title: "ReinChat 官网", URL: "https://example.com/reinchat", Snippet: "ReinChat 介绍"},
			{Title: "文档", URL: "https://example.com/docs", Snippet: "使用说明"},
		}
	}
	env.app.webFetch = func(ctx context.Context, rawURL string) string { return "页面正文内容" }

	content := env.app.executeToolCall(context.Background(), jobID, "call_1", "web_search", `{"query":"reinchat"}`)
	if !strings.Contains(content, "[1] ReinChat 官网") || !strings.Contains(content, "https://example.com/docs") {
		t.Fatalf("tool content = %q", content)
	}
	tools := env.app.relay.toolHistory(jobID)
	if len(tools) != 2 {
		t.Fatalf("tool events = %#v", tools)
	}
	running, done := tools[0], tools[1]
	if running.Status != "running" || running.Query != "reinchat" || running.CallID != "call_1" {
		t.Fatalf("running event = %#v", running)
	}
	if done.Status != "done" || done.Summary != "找到 2 个网页" || done.CallID != "call_1" {
		t.Fatalf("done event = %#v", done)
	}
	if strings.Contains(done.Detail, "不是指令") || !strings.Contains(done.Detail, "[1] ReinChat 官网") {
		t.Fatalf("done detail = %q", done.Detail)
	}
	if len(done.Sources) != 2 || done.Sources[0].Index != 1 || done.Sources[1].Index != 2 {
		t.Fatalf("done sources = %#v", done.Sources)
	}
	segments := env.app.relay.segmentHistory(jobID)
	if len(segments) != 1 || segments[0].Status != "done" || segments[0].Query != "reinchat" {
		t.Fatalf("segments = %#v", segments)
	}

	// A fetched URL that is already in the list is de-duplicated.
	env.app.executeToolCall(context.Background(), jobID, "call_2", "fetch_url", `{"url":"https://example.com/reinchat"}`)
	sources := env.app.relay.sourceHistory(jobID)
	if len(sources) != 2 {
		t.Fatalf("sources after fetch = %#v", sources)
	}
	segments = env.app.relay.segmentHistory(jobID)
	if len(segments) != 2 || segments[1].Type != "tool" || segments[1].URL != "https://example.com/reinchat" {
		t.Fatalf("segments after fetch = %#v", segments)
	}
}

func TestRelayFallbackSearchPublishesToolEventsAndCitations(t *testing.T) {
	env := newTestEnv(t)
	auth := env.register("fallback_user", "fallback password")
	userID := testUserID(t, env, auth.User.Username)
	jobID := "dddddddd-1111-4111-8111-111111111111"
	insertRelayJob(t, env, userID, jobID, "")

	var mu sync.Mutex
	var streamMessages []any
	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		if stream, _ := body["stream"].(bool); stream {
			mu.Lock()
			streamMessages, _ = body["messages"].([]any)
			mu.Unlock()
			w.Header().Set("Content-Type", "text/event-stream")
			fmt.Fprint(w, "data: {\"choices\":[{\"delta\":{\"content\":\"答案见 [1]\"}}]}\n\n")
			fmt.Fprint(w, "data: [DONE]\n\n")
			return
		}
		// The provider rejects tool definitions, forcing the fallback path.
		w.WriteHeader(http.StatusBadRequest)
		fmt.Fprint(w, `{"error":{"message":"tools are not supported"}}`)
	}))
	defer upstream.Close()

	env.app.webSearch = func(ctx context.Context, query string) []webResult {
		return []webResult{{Title: "ReinChat", URL: "https://example.com/reinchat", Snippet: "介绍"}}
	}
	env.app.webFetch = func(ctx context.Context, rawURL string) string { return "页面正文" }

	ch, cancel := env.app.relay.subscribe(jobID)
	provider := &relayProvider{
		ID: "provider-test", Protocol: "CHAT_COMPLETIONS", BaseURL: upstream.URL,
		APIKey: "test-key", Model: "test-model",
	}
	env.app.runRelayJob(context.Background(), userID, jobID, provider, "test-model", "", true,
		[]MessageDTO{{ID: "u1", Role: "user", Content: "reinchat 是什么", Images: []ImageDTO{}, Status: "COMPLETE"}})
	cancel()
	var sawRunning, sawDone bool
	var done relayEvent
	for event := range ch {
		if event.Type != "tool" {
			continue
		}
		if event.Status == "running" && event.Tool == "web_search" && event.Query == "reinchat 是什么" {
			sawRunning = true
		}
		if event.Status == "done" && event.Tool == "web_search" {
			sawDone = true
			done = event
		}
	}
	if !sawRunning || !sawDone {
		t.Fatalf("fallback tool events missing: running=%v done=%v", sawRunning, sawDone)
	}
	if done.Summary != "找到 1 个网页" || len(done.Sources) != 1 {
		t.Fatalf("fallback done event = %#v", done)
	}

	mu.Lock()
	messages := streamMessages
	mu.Unlock()
	foundInstruction := false
	for _, raw := range messages {
		message, _ := raw.(map[string]any)
		if message == nil || message["role"] != "system" {
			continue
		}
		content, _ := message["content"].(string)
		if strings.Contains(content, "唯一允许引用") && strings.Contains(content, "https://example.com/reinchat") {
			foundInstruction = true
		}
	}
	if !foundInstruction {
		t.Fatalf("fallback stream messages missing citation instruction: %#v", messages)
	}
}

func TestRelaySearchAgentPublishesTimelineAndPersistsArtifacts(t *testing.T) {
	env := newTestEnv(t)
	auth := env.register("agent_user", "agent password")
	userID := testUserID(t, env, auth.User.Username)
	jobID := "cccccccc-1111-4111-8111-111111111111"
	insertRelayJob(t, env, userID, jobID, "")

	upstream := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		var body map[string]any
		_ = json.NewDecoder(r.Body).Decode(&body)
		if stream, _ := body["stream"].(bool); stream {
			w.Header().Set("Content-Type", "text/event-stream")
			fmt.Fprint(w, "data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"思考B\"}}]}\n\n")
			fmt.Fprint(w, "data: {\"choices\":[{\"delta\":{\"content\":\"答案见 [1]\"}}]}\n\n")
			fmt.Fprint(w, "data: [DONE]\n\n")
			return
		}
		w.Header().Set("Content-Type", "application/json")
		fmt.Fprint(w, `{"choices":[{"message":{"content":"","reasoning_content":"思考A","tool_calls":[{"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"reinchat\"}"}}]}}]}`)
	}))
	defer upstream.Close()

	env.app.webSearch = func(ctx context.Context, query string) []webResult {
		return []webResult{{Title: "ReinChat", URL: "https://example.com/reinchat", Snippet: "介绍"}}
	}
	env.app.webFetch = func(ctx context.Context, rawURL string) string { return "" }

	ch, cancel := env.app.relay.subscribe(jobID)
	provider := &relayProvider{
		ID: "provider-test", Protocol: "CHAT_COMPLETIONS", BaseURL: upstream.URL,
		APIKey: "test-key", Model: "test-model",
	}
	env.app.runRelayJob(context.Background(), userID, jobID, provider, "test-model", "", true,
		[]MessageDTO{{ID: "u1", Role: "user", Content: "reinchat 是什么", Images: []ImageDTO{}, Status: "COMPLETE"}})
	cancel()
	events := make([]relayEvent, 0, 16)
	for event := range ch {
		events = append(events, event)
	}

	var timeline []string
	for _, event := range events {
		switch event.Type {
		case "reasoning":
			timeline = append(timeline, "reasoning:"+event.Text)
		case "tool":
			timeline = append(timeline, "tool:"+event.Status)
		case "delta":
			timeline = append(timeline, "delta")
		case "status":
			timeline = append(timeline, "status:"+event.Status)
		}
	}
	want := []string{
		"reasoning:思考A",
		"status:tools",
		"tool:running",
		"tool:done",
		"reasoning:思考B",
		"delta",
		"status:completed",
	}
	if strings.Join(timeline, "|") != strings.Join(want, "|") {
		t.Fatalf("timeline = %v, want %v", timeline, want)
	}

	artifacts := env.app.loadRelayArtifacts(context.Background(), jobID)
	if len(artifacts.Segments) != 3 ||
		artifacts.Segments[0].Type != "reasoning" ||
		artifacts.Segments[1].Type != "tool" || artifacts.Segments[1].Query != "reinchat" ||
		artifacts.Segments[2].Type != "reasoning" {
		t.Fatalf("persisted segments = %#v", artifacts.Segments)
	}
	if len(artifacts.Tools) != 2 || artifacts.Tools[1].Summary != "找到 1 个网页" {
		t.Fatalf("persisted tools = %#v", artifacts.Tools)
	}
	if len(artifacts.Sources) != 1 || artifacts.Sources[0].URL != "https://example.com/reinchat" {
		t.Fatalf("persisted sources = %#v", artifacts.Sources)
	}
	job, err := env.app.loadRelayJob(context.Background(), userID, jobID)
	if err != nil {
		t.Fatalf("load job: %v", err)
	}
	if job.Status != "completed" || job.Text != "答案见 [1]" {
		t.Fatalf("job = %#v", job)
	}
}
