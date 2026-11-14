package main

import (
	"database/sql"
	"math"
	"net/http"
	"net/url"
	"strings"
	"unicode"
	"unicode/utf8"
)

func (s *Server) handleListProviders(w http.ResponseWriter, r *http.Request) {
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	rows, err := s.db.QueryContext(r.Context(),
		`SELECT id, name, protocol, base_url, api_key_enc, model, system_prompt, temperature, max_tokens
		 FROM providers WHERE user_id = ? ORDER BY name COLLATE NOCASE, id`, user.ID)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	defer rows.Close()

	providers := make([]ProviderDTO, 0)
	for rows.Next() {
		var p ProviderDTO
		var encryptedKey []byte
		var temperature sql.NullFloat64
		var maxTokens sql.NullInt64
		if err := rows.Scan(&p.ID, &p.Name, &p.Protocol, &p.BaseURL, &encryptedKey,
			&p.Model, &p.SystemPrompt, &temperature, &maxTokens); err != nil {
			s.internalError(w, r, err)
			return
		}
		key, err := s.aead.open(encryptedKey, providerAAD(user.ID, p.ID))
		if err != nil {
			s.internalError(w, r, err)
			return
		}
		p.APIKey = string(key)
		if temperature.Valid {
			v := temperature.Float64
			p.Temperature = &v
		}
		if maxTokens.Valid {
			v := int(maxTokens.Int64)
			p.MaxTokens = &v
		}
		providers = append(providers, p)
	}
	if err := rows.Err(); err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, ProvidersResponse{Providers: providers})
}

func (s *Server) handleProviderItem(w http.ResponseWriter, r *http.Request, providerID string) {
	switch r.Method {
	case http.MethodPut, http.MethodDelete:
	default:
		methodNotAllowed(w, http.MethodPut, http.MethodDelete)
		return
	}
	user, ok := s.requireAuth(w, r)
	if !ok {
		return
	}
	if err := validateProviderID(providerID); err != nil {
		writeRequestError(w, err)
		return
	}

	switch r.Method {
	case http.MethodPut:
		s.putProvider(w, r, user, providerID)
	case http.MethodDelete:
		s.deleteProvider(w, r, user, providerID)
	}
}

func (s *Server) putProvider(w http.ResponseWriter, r *http.Request, user *authUser, providerID string) {
	var provider ProviderDTO
	if e := decodeJSONBody(w, r, &provider, maxProviderJSONBytes); e != nil {
		writeRequestError(w, e)
		return
	}
	if provider.ID != providerID {
		writeAPIError(w, http.StatusBadRequest, "validation_error", "provider id in body must match the URL")
		return
	}
	if e := validateProvider(&provider); e != nil {
		writeRequestError(w, e)
		return
	}

	encryptedKey, err := s.aead.seal([]byte(provider.APIKey), providerAAD(user.ID, provider.ID))
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	var temperature any
	if provider.Temperature != nil {
		temperature = *provider.Temperature
	}
	var maxTokens any
	if provider.MaxTokens != nil {
		maxTokens = *provider.MaxTokens
	}
	now := s.now().UnixMilli()
	_, err = s.db.ExecContext(r.Context(),
		`INSERT INTO providers
		 (user_id, id, name, protocol, base_url, api_key_enc, model, system_prompt, temperature, max_tokens, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
		 ON CONFLICT(user_id, id) DO UPDATE SET
		   name = excluded.name,
		   protocol = excluded.protocol,
		   base_url = excluded.base_url,
		   api_key_enc = excluded.api_key_enc,
		   model = excluded.model,
		   system_prompt = excluded.system_prompt,
		   temperature = excluded.temperature,
		   max_tokens = excluded.max_tokens,
		   updated_at = excluded.updated_at`,
		user.ID, provider.ID, provider.Name, provider.Protocol, provider.BaseURL, encryptedKey,
		provider.Model, provider.SystemPrompt, temperature, maxTokens, now)
	if err != nil {
		s.internalError(w, r, err)
		return
	}
	writeJSON(w, http.StatusOK, provider)
}

func (s *Server) deleteProvider(w http.ResponseWriter, r *http.Request, user *authUser, providerID string) {
	if _, err := s.db.ExecContext(r.Context(),
		`DELETE FROM providers WHERE user_id = ? AND id = ?`, user.ID, providerID); err != nil {
		s.internalError(w, r, err)
		return
	}
	writeNoContent(w)
}

func validateProviderID(id string) *apiError {
	if id == "" {
		return invalidField("id", "provider id must not be empty")
	}
	if utf8.RuneCountInString(id) > 100 || len(id) > 400 {
		return invalidField("id", "provider id must be at most 100 characters")
	}
	if strings.ContainsAny(id, "/?#") {
		return invalidField("id", "provider id contains invalid characters")
	}
	for _, r := range id {
		if unicode.IsControl(r) {
			return invalidField("id", "provider id contains control characters")
		}
	}
	return nil
}

func validateProvider(p *ProviderDTO) *apiError {
	if e := validateProviderID(p.ID); e != nil {
		return e
	}
	if utf8.RuneCountInString(p.Name) > 200 {
		return invalidField("name", "name must be at most 200 characters")
	}
	if p.Protocol != "RESPONSES" && p.Protocol != "CHAT_COMPLETIONS" {
		return invalidField("protocol", "protocol must be RESPONSES or CHAT_COMPLETIONS")
	}
	if len(p.BaseURL) > 2048 {
		return invalidField("baseUrl", "baseUrl must be at most 2048 bytes")
	}
	if p.BaseURL != "" {
		u, err := url.Parse(p.BaseURL)
		if err != nil || (!strings.EqualFold(u.Scheme, "http") && !strings.EqualFold(u.Scheme, "https")) || u.Host == "" {
			return invalidField("baseUrl", "baseUrl must be an absolute http or https URL")
		}
	}
	if len(p.APIKey) > 8192 {
		return invalidField("apiKey", "apiKey must be at most 8192 bytes")
	}
	if utf8.RuneCountInString(p.Model) > 200 {
		return invalidField("model", "model must be at most 200 characters")
	}
	if len(p.SystemPrompt) > 100_000 {
		return invalidField("systemPrompt", "systemPrompt must be at most 100000 bytes")
	}
	if p.Temperature != nil && (math.IsNaN(*p.Temperature) || math.IsInf(*p.Temperature, 0)) {
		return invalidField("temperature", "temperature must be a finite number or null")
	}
	if p.MaxTokens != nil && (*p.MaxTokens < 0 || *p.MaxTokens > 100_000_000) {
		return invalidField("maxTokens", "maxTokens must be between 0 and 100000000 or null")
	}
	return nil
}
