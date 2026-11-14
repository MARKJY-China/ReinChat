package main

import (
	"bytes"
	"net/http"
	"testing"
)

func TestProviderUpsertListDeleteAndEncryption(t *testing.T) {
	env := newTestEnv(t)
	user := env.register("provider_user", "provider password")
	providerID := "f47ac10b-58cc-4372-a567-0e02b2c3d479"
	apiKey := "sk-super-secret-provider-key"

	body := ProviderDTO{
		ID:           providerID,
		Name:         "Main provider",
		Protocol:     "RESPONSES",
		BaseURL:      "https://api.example.com/v1",
		APIKey:       apiKey,
		Model:        "gpt-test",
		SystemPrompt: "Be concise.",
		Temperature:  floatPtr(0.7),
		MaxTokens:    intPtr(2048),
	}
	created := env.doJSON(http.MethodPut, "/api/v1/providers/"+providerID, user.Token, body)
	requireStatus(t, created, http.StatusOK)
	var createdBody ProviderDTO
	decodeBody(t, created, &createdBody)
	if createdBody.APIKey != apiKey || createdBody.Model != "gpt-test" ||
		createdBody.Temperature == nil || *createdBody.Temperature != 0.7 ||
		createdBody.MaxTokens == nil || *createdBody.MaxTokens != 2048 {
		t.Fatalf("unexpected provider response: %+v", createdBody)
	}

	var encrypted []byte
	if err := env.app.db.QueryRow(`SELECT api_key_enc FROM providers WHERE user_id = ? AND id = ?`,
		findUserID(t, env, "provider_user"), providerID).Scan(&encrypted); err != nil {
		t.Fatalf("query encrypted key: %v", err)
	}
	if bytes.Contains(encrypted, []byte(apiKey)) || string(encrypted) == apiKey {
		t.Fatalf("provider API key is stored as plaintext")
	}
	if len(encrypted) < 1+12+16 {
		t.Fatalf("encrypted key is too short: %d", len(encrypted))
	}

	list := env.doJSON(http.MethodGet, "/api/v1/providers", user.Token, nil)
	requireStatus(t, list, http.StatusOK)
	var providers ProvidersResponse
	decodeBody(t, list, &providers)
	if len(providers.Providers) != 1 || providers.Providers[0].APIKey != apiKey {
		t.Fatalf("provider round trip failed: %+v", providers)
	}

	updated := body
	updated.Name = "Updated provider"
	updated.APIKey = "sk-rotated-secret"
	updated.Model = "gpt-updated"
	updated.Temperature = nil
	updated.MaxTokens = nil
	up := env.doJSON(http.MethodPut, "/api/v1/providers/"+providerID, user.Token, updated)
	requireStatus(t, up, http.StatusOK)
	list = env.doJSON(http.MethodGet, "/api/v1/providers", user.Token, nil)
	requireStatus(t, list, http.StatusOK)
	decodeBody(t, list, &providers)
	if len(providers.Providers) != 1 || providers.Providers[0].Name != "Updated provider" ||
		providers.Providers[0].APIKey != "sk-rotated-secret" ||
		providers.Providers[0].Temperature != nil || providers.Providers[0].MaxTokens != nil {
		t.Fatalf("provider upsert did not replace row: %+v", providers.Providers)
	}

	mismatch := env.doJSON(http.MethodPut, "/api/v1/providers/"+providerID, user.Token, func() ProviderDTO {
		other := body
		other.ID = "00000000-0000-4000-8000-000000000000"
		return other
	}())
	requireStatus(t, mismatch, http.StatusBadRequest)

	badProtocol := body
	badProtocol.Protocol = "UNKNOWN"
	bad := env.doJSON(http.MethodPut, "/api/v1/providers/"+providerID, user.Token, badProtocol)
	requireStatus(t, bad, http.StatusBadRequest)

	otherUser := env.register("provider_user2", "provider password")
	otherList := env.doJSON(http.MethodGet, "/api/v1/providers", otherUser.Token, nil)
	requireStatus(t, otherList, http.StatusOK)
	var otherProviders ProvidersResponse
	decodeBody(t, otherList, &otherProviders)
	if len(otherProviders.Providers) != 0 {
		t.Fatalf("provider leaked across users: %+v", otherProviders.Providers)
	}

	deleted := env.doJSON(http.MethodDelete, "/api/v1/providers/"+providerID, user.Token, nil)
	requireStatus(t, deleted, http.StatusNoContent)
	list = env.doJSON(http.MethodGet, "/api/v1/providers", user.Token, nil)
	requireStatus(t, list, http.StatusOK)
	decodeBody(t, list, &providers)
	if len(providers.Providers) != 0 {
		t.Fatalf("providers after delete = %+v", providers.Providers)
	}
}

func findUserID(t *testing.T, env *testEnv, username string) int64 {
	t.Helper()
	var id int64
	if err := env.app.db.QueryRow(`SELECT id FROM users WHERE username = ?`, username).Scan(&id); err != nil {
		t.Fatalf("query user id: %v", err)
	}
	return id
}

func floatPtr(v float64) *float64 { return &v }
func intPtr(v int) *int           { return &v }
