package main

import (
	"testing"
	"time"
)

func TestEventHubPublishesToSubscribersOnly(t *testing.T) {
	hub := newEventHub()
	ch, cancel := hub.subscribe(7)
	defer cancel()
	other, otherCancel := hub.subscribe(8)
	defer otherCancel()

	hub.publish(7, SyncEvent{Type: "conversation.updated", ConversationID: "c1", Version: 2})

	select {
	case event := <-ch:
		if event.ConversationID != "c1" || event.Version != 2 {
			t.Fatalf("unexpected event: %+v", event)
		}
	case <-time.After(time.Second):
		t.Fatal("subscriber did not receive event")
	}

	select {
	case event := <-other:
		t.Fatalf("other user received event: %+v", event)
	case <-time.After(50 * time.Millisecond):
	}
}

func TestEventHubSlowSubscriberDoesNotBlock(t *testing.T) {
	hub := newEventHub()
	_, cancel := hub.subscribe(9)
	defer cancel()
	done := make(chan struct{})
	go func() {
		for i := 0; i < 100; i++ {
			hub.publish(9, SyncEvent{Type: "conversation.updated"})
		}
		close(done)
	}()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("publish blocked on a slow subscriber")
	}
}
