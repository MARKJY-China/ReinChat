package main

import "testing"

func TestCleanWebText(t *testing.T) {
	got := cleanWebText("<html><script>bad()</script><style>.x{}</style><p>Hello&nbsp;<b>world</b></p></html>")
	if got != "Hello world" {
		t.Fatalf("unexpected text: %q", got)
	}
}

func TestPublicHostFilter(t *testing.T) {
	for _, host := range []string{"127.0.0.1", "localhost", "10.0.0.1", "192.168.1.2", "::1"} {
		if isPublicHost(host) {
			t.Fatalf("expected private host to be rejected: %s", host)
		}
	}
	if !isPublicHost("example.com") {
		t.Fatal("expected public host to be accepted")
	}
}
