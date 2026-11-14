package main

import "testing"

func TestParseDSMLToolCalls(t *testing.T) {
	raw := `< | | DSML | | calls>
< | | DSML | | invoke name="web_search">
< | | DSML | | parameter name="query" string="true">玄铁 语言 编译器 达摩院</ | | DSML | | parameter>
</ | | DSML | | invoke>
</ | | DSML | | calls>`
	calls := parseDSMLCalls(raw)
	if len(calls) != 1 {
		t.Fatalf("expected one DSML call, got %d", len(calls))
	}
	if calls[0].Function.Name != "web_search" {
		t.Fatalf("unexpected tool: %s", calls[0].Function.Name)
	}
	if calls[0].Function.Arguments != `{"query":"玄铁 语言 编译器 达摩院"}` {
		t.Fatalf("unexpected args: %s", calls[0].Function.Arguments)
	}
	if cleanDSMLText(raw) != "" {
		t.Fatalf("DSML should be removed: %q", cleanDSMLText(raw))
	}
}
