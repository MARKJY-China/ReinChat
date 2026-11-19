package main

import (
	"bytes"
	"net/http"
	"reflect"
	"testing"
	"time"
)

func TestConversationLifecycleAndVersionConflict(t *testing.T) {
	env := newTestEnv(t)
	user := env.register("chat_user", "conversation password")
	other := env.register("chat_user2", "conversation password")
	conversationID := "11111111-1111-4111-8111-111111111111"
	createdMillis := int64(1_700_000_000_000)
	originalUpdated := int64(1_700_000_100_000)

	createBody := ConversationDTO{
		ID:        conversationID,
		Title:     "First conversation",
		CreatedAt: createdMillis,
		UpdatedAt: originalUpdated,
		Version:   99, // must be ignored
		Messages: []MessageDTO{
			{
				ID:        "m1",
				Role:      "user",
				Content:   "Hello there",
				Images:    []ImageDTO{},
				CreatedAt: createdMillis,
				Status:    "COMPLETE",
			},
			{
				ID:        "m2",
				Role:      "assistant",
				Content:   "First assistant reply",
				Images:    []ImageDTO{},
				CreatedAt: createdMillis + 1,
				Status:    "COMPLETE",
			},
		},
	}
	created := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, createBody)
	requireStatus(t, created, http.StatusCreated)
	var conversation ConversationDTO
	decodeBody(t, created, &conversation)
	if conversation.Version != 1 || conversation.ID != conversationID ||
		conversation.CreatedAt != createdMillis || conversation.UpdatedAt != originalUpdated ||
		len(conversation.Messages) != 2 || conversation.Messages[1].Content != "First assistant reply" {
		t.Fatalf("unexpected created conversation: %+v", conversation)
	}

	duplicate := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, createBody)
	requireStatus(t, duplicate, http.StatusConflict)
	if code := errorCode(t, duplicate); code != "conversation_exists" {
		t.Fatalf("duplicate code = %q", code)
	}

	secondID := "22222222-2222-4222-8222-222222222222"
	second := createBody
	second.ID = secondID
	second.Title = "Second conversation"
	second.UpdatedAt = time.Now().Add(time.Hour).UnixMilli()
	secondCreated := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, second)
	requireStatus(t, secondCreated, http.StatusCreated)

	list := env.doJSON(http.MethodGet, "/api/v1/conversations", user.Token, nil)
	requireStatus(t, list, http.StatusOK)
	var summaries ConversationsResponse
	decodeBody(t, list, &summaries)
	if len(summaries.Conversations) != 2 {
		t.Fatalf("summary count = %d", len(summaries.Conversations))
	}
	if summaries.Conversations[0].ID != secondID {
		t.Fatalf("conversation list is not sorted by updatedAt desc: %+v", summaries.Conversations)
	}
	var firstSummary SummaryDTO
	for _, summary := range summaries.Conversations {
		if summary.ID == conversationID {
			firstSummary = summary
		}
	}
	if firstSummary.MessageCount != 2 || firstSummary.Preview != "First assistant reply" || firstSummary.Version != 1 {
		t.Fatalf("unexpected summary: %+v", firstSummary)
	}

	fetched := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, fetched, http.StatusOK)
	var fetchedConversation ConversationDTO
	decodeBody(t, fetched, &fetchedConversation)
	if fetchedConversation.Version != 1 || len(fetchedConversation.Messages) != 2 {
		t.Fatalf("unexpected fetched conversation: %+v", fetchedConversation)
	}
	crossUserGet := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, other.Token, nil)
	requireStatus(t, crossUserGet, http.StatusNotFound)

	updateTime := time.Now().UnixMilli()
	update := UpdateConversationRequest{
		BaseVersion: 1,
		Title:       "Updated title",
		CreatedAt:   createdMillis,
		UpdatedAt:   originalUpdated, // must be replaced by server time
		Messages: []MessageDTO{
			{
				ID:        "m1",
				Role:      "user",
				Content:   "Hello there",
				Images:    []ImageDTO{},
				CreatedAt: createdMillis,
				Status:    "COMPLETE",
			},
			{
				ID:        "m3",
				Role:      "assistant",
				Content:   "Second assistant reply",
				Images:    []ImageDTO{},
				CreatedAt: createdMillis + 2,
				Status:    "STOPPED",
			},
		},
	}
	updated := env.doJSON(http.MethodPut, "/api/v1/conversations/"+conversationID, user.Token, update)
	requireStatus(t, updated, http.StatusOK)
	var updatedConversation ConversationDTO
	decodeBody(t, updated, &updatedConversation)
	if updatedConversation.Version != 2 || updatedConversation.Title != "Updated title" ||
		updatedConversation.UpdatedAt < updateTime || updatedConversation.UpdatedAt == originalUpdated ||
		len(updatedConversation.Messages) != 2 || updatedConversation.Messages[1].Status != "STOPPED" {
		t.Fatalf("unexpected updated conversation: %+v", updatedConversation)
	}

	stale := update
	stale.Title = "Stale edit"
	conflict := env.doJSON(http.MethodPut, "/api/v1/conversations/"+conversationID, user.Token, stale)
	requireStatus(t, conflict, http.StatusConflict)
	var conflictBody VersionConflictResponse
	decodeBody(t, conflict, &conflictBody)
	if conflictBody.Error.Code != "version_conflict" || conflictBody.Conversation.Version != 2 ||
		conflictBody.Conversation.Title != "Updated title" {
		t.Fatalf("unexpected version conflict response: %+v", conflictBody)
	}

	crossUserUpdate := env.doJSON(http.MethodPut, "/api/v1/conversations/"+conversationID, other.Token, update)
	requireStatus(t, crossUserUpdate, http.StatusNotFound)

	deleteResp := env.doJSON(http.MethodDelete, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, deleteResp, http.StatusNoContent)
	deleteAgain := env.doJSON(http.MethodDelete, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, deleteAgain, http.StatusNoContent)
	gone := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, gone, http.StatusNotFound)

	list = env.doJSON(http.MethodGet, "/api/v1/conversations", user.Token, nil)
	requireStatus(t, list, http.StatusOK)
	decodeBody(t, list, &summaries)
	if len(summaries.Conversations) != 1 || summaries.Conversations[0].ID != secondID {
		t.Fatalf("unexpected conversation list after delete: %+v", summaries.Conversations)
	}
}

func TestConversationSegmentsAndSourcesRoundTrip(t *testing.T) {
	env := newTestEnv(t)
	user := env.register("timeline_roundtrip", "conversation password")
	conversationID := "77777777-7777-4777-8777-777777777777"
	now := int64(1_700_000_500_000)
	segments := []MessageSegment{
		{Type: "reasoning", Text: "思考A", Status: "done"},
		{
			Type: "tool", Name: "web_search", Label: "搜索网页", Status: "done",
			CallID: "call_1", Query: "reinchat", Summary: "找到 1 个网页",
			Detail: "[1] ReinChat\nhttps://example.com/reinchat",
		},
		{Type: "reasoning", Text: "思考B", Status: "done"},
	}
	sources := []SourceDTO{
		{Index: 1, Title: "ReinChat 官网", URL: "https://example.com/reinchat", Snippet: "介绍"},
	}
	createBody := ConversationDTO{
		ID: conversationID, Title: "Timeline round trip", CreatedAt: now, UpdatedAt: now,
		Messages: []MessageDTO{
			{ID: "u1", Role: "user", Content: "reinchat 是什么", Images: []ImageDTO{}, CreatedAt: now, Status: "COMPLETE"},
			{
				ID: "a1", Role: "assistant", Content: "答案[1]", Reasoning: "思考A思考B",
				Segments: segments, Sources: sources, Images: []ImageDTO{},
				CreatedAt: now + 1, Status: "COMPLETE",
			},
		},
	}
	created := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, createBody)
	requireStatus(t, created, http.StatusCreated)
	if !bytes.Contains(created.body, []byte(`"callId":"call_1"`)) ||
		!bytes.Contains(created.body, []byte(`"sources":[`)) {
		t.Fatalf("create response is missing timeline fields: %s", created.body)
	}

	fetched := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, fetched, http.StatusOK)
	var stored ConversationDTO
	decodeBody(t, fetched, &stored)
	if len(stored.Messages) != 2 {
		t.Fatalf("stored messages = %#v", stored.Messages)
	}
	if !reflect.DeepEqual(stored.Messages[1].Segments, segments) {
		t.Fatalf("segments round trip = %#v, want %#v", stored.Messages[1].Segments, segments)
	}
	if !reflect.DeepEqual(stored.Messages[1].Sources, sources) {
		t.Fatalf("sources round trip = %#v, want %#v", stored.Messages[1].Sources, sources)
	}

	updated := env.doJSON(http.MethodPut, "/api/v1/conversations/"+conversationID, user.Token, UpdateConversationRequest{
		BaseVersion: 1, Title: "Timeline round trip", CreatedAt: now, UpdatedAt: now,
		Messages: createBody.Messages,
	})
	requireStatus(t, updated, http.StatusOK)
	refetched := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, user.Token, nil)
	requireStatus(t, refetched, http.StatusOK)
	decodeBody(t, refetched, &stored)
	if !reflect.DeepEqual(stored.Messages[1].Segments, segments) ||
		!reflect.DeepEqual(stored.Messages[1].Sources, sources) {
		t.Fatalf("segments/sources lost after PUT: %#v / %#v", stored.Messages[1].Segments, stored.Messages[1].Sources)
	}

	unsafe := createBody
	unsafe.ID = "88888888-8888-4888-8888-888888888888"
	unsafe.Messages = append([]MessageDTO(nil), createBody.Messages...)
	unsafe.Messages[1].Sources = []SourceDTO{{Index: 1, Title: "bad", URL: "javascript:alert(1)"}}
	rejected := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, unsafe)
	requireStatus(t, rejected, http.StatusBadRequest)
}

func TestConversationValidationAndLimits(t *testing.T) {
	env := newTestEnv(t)
	user := env.register("validation_user", "conversation password")

	invalidID := ConversationDTO{
		ID: "not-a-uuid", Title: "x", CreatedAt: 1, UpdatedAt: 1, Messages: []MessageDTO{},
	}
	resp := env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, invalidID)
	requireStatus(t, resp, http.StatusBadRequest)

	badRole := invalidID
	badRole.ID = "33333333-3333-4333-8333-333333333333"
	badRole.Messages = []MessageDTO{
		{ID: "m1", Role: "system", Content: "x", Images: []ImageDTO{}, Status: "COMPLETE"},
	}
	resp = env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, badRole)
	requireStatus(t, resp, http.StatusBadRequest)

	tooMany := make([]MessageDTO, maxConversationMessages+1)
	for i := range tooMany {
		tooMany[i] = MessageDTO{ID: "m", Role: "user", Content: "x", Images: []ImageDTO{}, Status: "COMPLETE"}
	}
	tooManyBody := invalidID
	tooManyBody.ID = "44444444-4444-4444-8444-444444444444"
	tooManyBody.Messages = tooMany
	resp = env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, tooManyBody)
	requireStatus(t, resp, http.StatusBadRequest)

	empty := ConversationDTO{
		ID:        "66666666-6666-4666-8666-666666666666",
		Title:     "Empty",
		CreatedAt: 1,
		UpdatedAt: 1,
		Messages:  nil,
	}
	resp = env.doJSON(http.MethodPost, "/api/v1/conversations", user.Token, empty)
	requireStatus(t, resp, http.StatusCreated)
	if !bytes.Contains(resp.body, []byte(`"messages":[]`)) {
		t.Fatalf("empty messages must serialize as an array: %s", resp.body)
	}
	getEmpty := env.doJSON(http.MethodGet, "/api/v1/conversations/"+empty.ID, user.Token, nil)
	requireStatus(t, getEmpty, http.StatusOK)
	var emptyStored ConversationDTO
	decodeBody(t, getEmpty, &emptyStored)
	if emptyStored.Messages == nil || len(emptyStored.Messages) != 0 {
		t.Fatalf("empty messages round trip = %#v", emptyStored.Messages)
	}
}
