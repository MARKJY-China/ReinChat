package main

import (
	"testing"
	"time"
)

func TestRateLimiter(t *testing.T) {
	current := time.Unix(1_700_000_000, 0)
	limiter := newRateLimiter(2, time.Minute, func() time.Time { return current })

	if ok, _ := limiter.Allow("127.0.0.1"); !ok {
		t.Fatal("first attempt should be allowed")
	}
	if ok, _ := limiter.Allow("127.0.0.1"); !ok {
		t.Fatal("second attempt should be allowed")
	}
	ok, retry := limiter.Allow("127.0.0.1")
	if ok || retry <= 0 {
		t.Fatalf("third attempt should be limited, retry=%v", retry)
	}
	if ok, _ := limiter.Allow("192.0.2.10"); !ok {
		t.Fatal("different IP should not be limited")
	}

	current = current.Add(time.Minute)
	if ok, _ := limiter.Allow("127.0.0.1"); !ok {
		t.Fatal("window should reset")
	}
}
