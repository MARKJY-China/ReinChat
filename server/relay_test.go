package main

import "testing"

func TestParseRelayDelta(t *testing.T) {
	text, reasoning, done := parseRelayDelta("CHAT_COMPLETIONS",
		`{"choices":[{"delta":{"content":"hello","reasoning_content":"think"}}]}`)
	if text != "hello" || reasoning != "think" || done {
		t.Fatalf("unexpected chat delta: %q %q %v", text, reasoning, done)
	}
	text, reasoning, done = parseRelayDelta("RESPONSES",
		`{"type":"response.output_text.delta","delta":" world"}`)
	if text != " world" || reasoning != "" || done {
		t.Fatalf("unexpected responses delta: %q %q %v", text, reasoning, done)
	}
	_, _, done = parseRelayDelta("RESPONSES", `{"type":"response.completed"}`)
	if !done {
		t.Fatal("expected completion")
	}
}
