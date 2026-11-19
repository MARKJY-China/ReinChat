package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"
	"time"
)

type ProviderModelsResponse struct {
	Models []ProviderModel `json:"models"`
}

type ProviderModelsFetch struct {
	BaseURL string `json:"baseUrl"`
	APIKey  string `json:"apiKey"`
}

func (s *Server) handleProviderModelsFetch(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		methodNotAllowed(w, http.MethodPost)
		return
	}
	if _, ok := s.requireAuth(w, r); !ok {
		return
	}
	var req ProviderModelsFetch
	if e := decodeJSONBody(w, r, &req, 32<<10); e != nil {
		writeRequestError(w, e)
		return
	}
	if strings.TrimSpace(req.BaseURL) == "" {
		writeAPIError(w, http.StatusBadRequest, "invalid_base_url", "Base URL is required")
		return
	}
	models, err := s.fetchProviderModels(r.Context(), req.BaseURL, req.APIKey)
	if err != nil {
		writeAPIError(w, http.StatusBadGateway, "models_unavailable", err.Error())
		return
	}
	writeJSON(w, http.StatusOK, ProviderModelsResponse{Models: models})
}

func (s *Server) fetchProviderModels(ctx context.Context, baseURL, apiKey string) ([]ProviderModel, error) {
	base := strings.TrimRight(strings.TrimSpace(baseURL), "/")
	if base == "" {
		return nil, errors.New("provider base URL is empty")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, base+"/models", nil)
	if err != nil {
		return nil, err
	}
	if apiKey != "" {
		req.Header.Set("Authorization", "Bearer "+apiKey)
	}
	resp, err := (&http.Client{Timeout: 20 * time.Second}).Do(req)
	if err != nil {
		return nil, errors.New("无法连接模型目录")
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, errors.New(upstreamError(resp.StatusCode, raw))
	}
	var root any
	if err := json.Unmarshal(raw, &root); err != nil {
		return nil, errors.New("模型目录返回了无效 JSON")
	}
	items := extractModelItems(root)
	models := make([]ProviderModel, 0, len(items))
	seen := map[string]bool{}
	for _, item := range items {
		id := strings.TrimSpace(stringValue(item, "id"))
		if id == "" || seen[id] {
			continue
		}
		seen[id] = true
		name := strings.TrimSpace(stringValue(item, "name"))
		if name == "" {
			name = strings.TrimSpace(stringValue(item, "display_name"))
		}
		if name == "" {
			name = id
		}
		models = append(models, ProviderModel{ID: id, Name: name})
	}
	return models, nil
}

func (s *Server) handleProviderModels(w http.ResponseWriter, r *http.Request, providerID string) {
	if r.Method != http.MethodGet {
		methodNotAllowed(w, http.MethodGet)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	provider, err := s.loadRelayProvider(r.Context(), user.ID, providerID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			writeAPIError(w, http.StatusNotFound, "provider_not_found", "provider not found")
			return
		}
		s.internalError(w, r, err)
		return
	}
	base := strings.TrimRight(strings.TrimSpace(provider.BaseURL), "/")
	if base == "" {
		writeAPIError(w, http.StatusBadRequest, "invalid_base_url", "provider base URL is empty")
		return
	}
	req, err := http.NewRequestWithContext(r.Context(), http.MethodGet, base+"/models", nil)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	if provider.APIKey != "" {
		req.Header.Set("Authorization", "Bearer "+provider.APIKey)
	}
	resp, err := (&http.Client{Timeout: 20 * time.Second}).Do(req)
	if err != nil {
		writeAPIError(w, http.StatusBadGateway, "models_unavailable", "无法连接模型目录")
		return
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		writeAPIError(w, http.StatusBadGateway, "models_unavailable", upstreamError(resp.StatusCode, raw))
		return
	}
	var root any
	if err := json.Unmarshal(raw, &root); err != nil {
		writeAPIError(w, http.StatusBadGateway, "models_invalid", "模型目录返回了无效 JSON")
		return
	}
	items := extractModelItems(root)
	models := make([]ProviderModel, 0, len(items))
	seen := map[string]bool{}
	for _, item := range items {
		id := strings.TrimSpace(stringValue(item, "id"))
		if id == "" || seen[id] {
			continue
		}
		seen[id] = true
		name := strings.TrimSpace(stringValue(item, "name"))
		if name == "" {
			name = strings.TrimSpace(stringValue(item, "display_name"))
		}
		if name == "" {
			name = id
		}
		models = append(models, ProviderModel{ID: id, Name: name})
	}
	writeJSON(w, http.StatusOK, ProviderModelsResponse{Models: models})
}

func extractModelItems(root any) []map[string]any {
	switch value := root.(type) {
	case []any:
		return mapsFromSlice(value)
	case map[string]any:
		for _, key := range []string{"data", "models", "items"} {
			if list, ok := value[key].([]any); ok {
				return mapsFromSlice(list)
			}
		}
	}
	return nil
}

func mapsFromSlice(list []any) []map[string]any {
	result := make([]map[string]any, 0, len(list))
	for _, item := range list {
		if obj, ok := item.(map[string]any); ok {
			result = append(result, obj)
		}
	}
	return result
}

func stringValue(obj map[string]any, key string) string {
	value, _ := obj[key].(string)
	return value
}
