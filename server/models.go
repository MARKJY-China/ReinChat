package main

// The structs in this file define the public wire format. Keep field names and
// nullability in sync with the Android client and the API contract.

type UserDTO struct {
	Username string `json:"username"`
	Level    int    `json:"level"`
}

type AuthResponse struct {
	Token     string  `json:"token"`
	ExpiresAt int64   `json:"expiresAt"`
	User      UserDTO `json:"user"`
}

type ProviderDTO struct {
	ID           string   `json:"id"`
	Name         string   `json:"name"`
	Protocol     string   `json:"protocol"`
	BaseURL      string   `json:"baseUrl"`
	APIKey       string   `json:"apiKey"`
	Model        string   `json:"model"`
	SystemPrompt string   `json:"systemPrompt"`
	Temperature  *float64 `json:"temperature"`
	MaxTokens    *int     `json:"maxTokens"`
}

type ImageDTO struct {
	ID       string  `json:"id"`
	RemoteID *string `json:"remoteId"`
	MimeType string  `json:"mimeType"`
	Width    int     `json:"width"`
	Height   int     `json:"height"`
}

type MessageDTO struct {
	ID        string     `json:"id"`
	Role      string     `json:"role"`
	Content   string     `json:"content"`
	Images    []ImageDTO `json:"images"`
	CreatedAt int64      `json:"createdAt"`
	Status    string     `json:"status"`
	Error     *string    `json:"error"`
}

type ConversationDTO struct {
	ID        string       `json:"id"`
	Title     string       `json:"title"`
	CreatedAt int64        `json:"createdAt"`
	UpdatedAt int64        `json:"updatedAt"`
	Version   int64        `json:"version"`
	Messages  []MessageDTO `json:"messages"`
}

type UpdateConversationRequest struct {
	BaseVersion int64        `json:"baseVersion"`
	Title       string       `json:"title"`
	CreatedAt   int64        `json:"createdAt"`
	UpdatedAt   int64        `json:"updatedAt"`
	Messages    []MessageDTO `json:"messages"`
}

type SummaryDTO struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	CreatedAt    int64  `json:"createdAt"`
	UpdatedAt    int64  `json:"updatedAt"`
	Version      int64  `json:"version"`
	MessageCount int    `json:"messageCount"`
	Preview      string `json:"preview"`
}

type FileUploadResponse struct {
	ID       string `json:"id"`
	MimeType string `json:"mimeType"`
	Size     int64  `json:"size"`
}

type ErrorBody struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type ErrorResponse struct {
	Error ErrorBody `json:"error"`
}

type VersionConflictResponse struct {
	Error        ErrorBody       `json:"error"`
	Conversation ConversationDTO `json:"conversation"`
}

type ProvidersResponse struct {
	Providers []ProviderDTO `json:"providers"`
}

type ConversationsResponse struct {
	Conversations []SummaryDTO `json:"conversations"`
}

type MeResponse struct {
	User UserDTO `json:"user"`
}

// apiError is an internal error representation that is safe to send to clients.
type apiError struct {
	status  int
	code    string
	message string
}

func (e *apiError) Error() string { return e.code + ": " + e.message }
