package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"
)

// SyncEvent is a lightweight notification. Clients should re-fetch the
// conversation/list from the REST API rather than trusting the event payload
// as a full snapshot.
type SyncEvent struct {
	Type           string `json:"type"`
	ConversationID string `json:"conversationId"`
	Version        int64  `json:"version,omitempty"`
	UpdatedAt      int64  `json:"updatedAt,omitempty"`
	Deleted        bool   `json:"deleted,omitempty"`
}

type eventHub struct {
	mu   sync.Mutex
	subs map[int64]map[chan SyncEvent]struct{}
}

func newEventHub() *eventHub {
	return &eventHub{subs: make(map[int64]map[chan SyncEvent]struct{})}
}

func (h *eventHub) subscribe(userID int64) (<-chan SyncEvent, func()) {
	ch := make(chan SyncEvent, 32)
	h.mu.Lock()
	if h.subs[userID] == nil {
		h.subs[userID] = make(map[chan SyncEvent]struct{})
	}
	h.subs[userID][ch] = struct{}{}
	h.mu.Unlock()
	cancel := func() {
		h.mu.Lock()
		if set := h.subs[userID]; set != nil {
			if _, ok := set[ch]; ok {
				delete(set, ch)
				close(ch)
			}
			if len(set) == 0 {
				delete(h.subs, userID)
			}
		}
		h.mu.Unlock()
	}
	return ch, cancel
}

func (h *eventHub) publish(userID int64, event SyncEvent) {
	h.mu.Lock()
	defer h.mu.Unlock()
	for ch := range h.subs[userID] {
		select {
		case ch <- event:
		default:
			// A slow client should not block conversation writes. The client
			// can always recover with a full REST refresh.
		}
	}
}

func (s *Server) handleEvents(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	flusher, ok := w.(http.Flusher)
	if !ok {
		writeAPIError(w, http.StatusInternalServerError, "stream_unsupported", "streaming is not supported")
		return
	}
	w.Header().Set("Content-Type", "text/event-stream; charset=utf-8")
	w.Header().Set("Cache-Control", "no-cache, no-transform")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
	_, _ = fmt.Fprint(w, ": connected\n\n")
	flusher.Flush()

	ch, cancel := s.events.subscribe(user.ID)
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
			payload, err := json.Marshal(event)
			if err != nil {
				continue
			}
			if _, err := fmt.Fprintf(w, "event: sync\ndata: %s\n\n", payload); err != nil {
				return
			}
			flusher.Flush()
		}
	}
}
