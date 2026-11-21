package main

import (
	"testing"
	"time"
)

func TestRelayHubDeliversTerminalEventWhenBufferIsFull(t *testing.T) {
	hub := newRelayHub()
	jobID := "terminal-test"
	ch, cancel := hub.subscribe(jobID)
	defer cancel()

	cap := cap(ch)
	for i := 0; i < cap; i++ {
		hub.publish(jobID, relayEvent{Type: "delta", Text: "x"})
	}
	done := make(chan struct{})
	go func() {
		hub.publish(jobID, relayEvent{Type: "status", Status: "completed"})
		close(done)
	}()

	// Drain until the terminal event shows up.
	deadline := time.After(5 * time.Second)
	for {
		select {
		case event := <-ch:
			if event.Type == "status" && event.Status == "completed" {
				<-done
				return
			}
		case <-deadline:
			t.Fatal("terminal event was dropped when the buffer was full")
		}
	}
}
