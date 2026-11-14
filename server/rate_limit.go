package main

import (
	"sync"
	"time"
)

type rateEntry struct {
	windowStart time.Time
	count       int
	lastSeen    time.Time
}

// rateLimiter is a deliberately small per-IP fixed-window limiter. It is
// process-local, which is appropriate for the single-instance v0.2 deployment.
type rateLimiter struct {
	mu          sync.Mutex
	limit       int
	window      time.Duration
	entries     map[string]rateEntry
	lastCleanup time.Time
	now         func() time.Time
}

func newRateLimiter(limit int, window time.Duration, now func() time.Time) *rateLimiter {
	if now == nil {
		now = time.Now
	}
	return &rateLimiter{
		limit:   limit,
		window:  window,
		entries: make(map[string]rateEntry),
		now:     now,
	}
}

func (l *rateLimiter) Allow(key string) (bool, time.Duration) {
	now := l.now()
	l.mu.Lock()
	defer l.mu.Unlock()

	if l.lastCleanup.IsZero() || now.Sub(l.lastCleanup) >= l.window {
		for k, entry := range l.entries {
			if now.Sub(entry.lastSeen) >= 2*l.window {
				delete(l.entries, k)
			}
		}
		l.lastCleanup = now
	}

	entry, exists := l.entries[key]
	if !exists || now.Sub(entry.windowStart) >= l.window {
		l.entries[key] = rateEntry{windowStart: now, count: 1, lastSeen: now}
		return true, 0
	}
	entry.lastSeen = now
	if entry.count >= l.limit {
		l.entries[key] = entry
		retry := l.window - now.Sub(entry.windowStart)
		if retry < time.Second {
			retry = time.Second
		}
		return false, retry
	}
	entry.count++
	l.entries[key] = entry
	return true, 0
}
