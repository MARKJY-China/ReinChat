package main

import (
	"bytes"
	"net/http"
	"testing"
)

func TestFileUploadDownloadAndCrossUserDenial(t *testing.T) {
	env := newTestEnv(t)
	owner := env.register("file_owner", "file upload password")
	other := env.register("file_other", "file upload password")

	upload := env.doMultipart("/api/v1/files", owner.Token, "file", "pixel.png", testPNG)
	requireStatus(t, upload, http.StatusCreated)
	var uploaded FileUploadResponse
	decodeBody(t, upload, &uploaded)
	if !isUUID(uploaded.ID) || uploaded.MimeType != "image/png" || uploaded.Size != int64(len(testPNG)) {
		t.Fatalf("unexpected upload response: %+v", uploaded)
	}

	download := env.doJSON(http.MethodGet, "/api/v1/files/"+uploaded.ID, owner.Token, nil)
	requireStatus(t, download, http.StatusOK)
	if !bytes.Equal(download.body, testPNG) {
		t.Fatalf("downloaded bytes differ")
	}
	if download.header.Get("Content-Type") != "image/png" {
		t.Fatalf("content type = %q", download.header.Get("Content-Type"))
	}
	if download.header.Get("X-Content-Type-Options") != "nosniff" {
		t.Fatalf("missing nosniff header")
	}

	otherDownload := env.doJSON(http.MethodGet, "/api/v1/files/"+uploaded.ID, other.Token, nil)
	requireStatus(t, otherDownload, http.StatusNotFound)
	unknownFile := env.doJSON(http.MethodGet, "/api/v1/files/00000000-0000-4000-8000-000000000000", owner.Token, nil)
	requireStatus(t, unknownFile, http.StatusNotFound)
	unauthorized := env.doJSON(http.MethodGet, "/api/v1/files/"+uploaded.ID, "", nil)
	requireStatus(t, unauthorized, http.StatusUnauthorized)

	unsupported := env.doMultipart("/api/v1/files", owner.Token, "file", "notes.txt", []byte("this is not an image"))
	requireStatus(t, unsupported, http.StatusUnsupportedMediaType)
	if code := errorCode(t, unsupported); code != "unsupported_media_type" {
		t.Fatalf("unsupported code = %q", code)
	}
	missingField := env.doMultipart("/api/v1/files", owner.Token, "wrongField", "pixel.png", testPNG)
	requireStatus(t, missingField, http.StatusBadRequest)
	if code := errorCode(t, missingField); code != "file_required" {
		t.Fatalf("missing field code = %q", code)
	}

	conversationID := "55555555-5555-4555-8555-555555555555"
	remoteID := uploaded.ID
	imageMessage := MessageDTO{
		ID:      "message-with-image",
		Role:    "user",
		Content: "Look at this",
		Images: []ImageDTO{{
			ID:       "local-image-1",
			RemoteID: &remoteID,
			MimeType: uploaded.MimeType,
			Width:    1,
			Height:   1,
		}},
		CreatedAt: 1_700_000_000_000,
		Status:    "COMPLETE",
	}
	conversationBody := ConversationDTO{
		ID:        conversationID,
		Title:     "Image message",
		CreatedAt: 1_700_000_000_000,
		UpdatedAt: 1_700_000_000_000,
		Messages:  []MessageDTO{imageMessage},
	}

	crossUserConversation := env.doJSON(http.MethodPost, "/api/v1/conversations", other.Token, conversationBody)
	requireStatus(t, crossUserConversation, http.StatusBadRequest)
	if code := errorCode(t, crossUserConversation); code != "invalid_image" {
		t.Fatalf("cross-user image code = %q", code)
	}
	notCreated := env.doJSON(http.MethodGet, "/api/v1/conversations/"+conversationID, other.Token, nil)
	requireStatus(t, notCreated, http.StatusNotFound)

	ownConversation := env.doJSON(http.MethodPost, "/api/v1/conversations", owner.Token, conversationBody)
	requireStatus(t, ownConversation, http.StatusCreated)
	var stored ConversationDTO
	decodeBody(t, ownConversation, &stored)
	if len(stored.Messages) != 1 || len(stored.Messages[0].Images) != 1 ||
		stored.Messages[0].Images[0].RemoteID == nil ||
		*stored.Messages[0].Images[0].RemoteID != uploaded.ID {
		t.Fatalf("image reference was not preserved: %+v", stored)
	}

	localOnly := conversationBody
	localOnly.ID = "77777777-7777-4777-8777-777777777777"
	localOnly.Messages = []MessageDTO{imageMessage}
	localOnly.Messages[0].Images = []ImageDTO{{
		ID:       "local-image-only",
		RemoteID: nil,
		MimeType: "image/png",
		Width:    1,
		Height:   1,
	}}
	localCreated := env.doJSON(http.MethodPost, "/api/v1/conversations", owner.Token, localOnly)
	requireStatus(t, localCreated, http.StatusCreated)
	if !bytes.Contains(localCreated.body, []byte(`"remoteId":null`)) {
		t.Fatalf("null remoteId was not preserved: %s", localCreated.body)
	}
}
