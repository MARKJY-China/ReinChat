export type Role = "USER" | "ASSISTANT";
export type MessageStatus = "COMPLETE" | "STREAMING" | "STOPPED" | "ERROR";
export type Protocol = "RESPONSES" | "CHAT_COMPLETIONS";

export interface ChatImage {
  id: string;
  dataUrl: string;
  mimeType: string;
  width: number;
  height: number;
  remoteId?: string | null;
}

export interface ChatMessage {
  id: string;
  role: Role;
  content: string;
  images: ChatImage[];
  createdAt: number;
  status: MessageStatus;
  error?: string | null;
}

export interface Conversation {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  messages: ChatMessage[];
  cloud: boolean;
  cloudVersion: number;
}

export interface Provider {
  id: string;
  name: string;
  protocol: Protocol;
  baseUrl: string;
  apiKey: string;
  model: string;
  systemPrompt: string;
  temperature: number | null;
  maxTokens: number | null;
  selectedModel: string;
  localOnly: boolean;
  cloudSynced: boolean;
}

export interface CloudAccount {
  username: string;
  serverUrl: string;
  token: string;
  expiresAt: number;
}

export interface AppState {
  version: number;
  providers: Provider[];
  activeProviderId: string | null;
  conversations: Conversation[];
  activeConversationId: string | null;
  cloudAccount: CloudAccount | null;
  cloudServerUrl: string;
  newChatCloud: boolean;
  pendingCloudConversationDeletes: string[];
  pendingCloudProviderDeletes: string[];
}

export interface CloudProviderDto {
  id: string;
  name: string;
  protocol: Protocol;
  baseUrl: string;
  apiKey: string;
  model: string;
  systemPrompt: string;
  temperature: number | null;
  maxTokens: number | null;
}

export interface CloudImageDto {
  id?: string;
  remoteId?: string | null;
  mimeType?: string;
  width?: number;
  height?: number;
}

export interface CloudMessageDto {
  id?: string;
  role: string;
  content: string;
  images?: CloudImageDto[];
  createdAt?: number;
  status?: string;
  error?: string | null;
}

export interface CloudConversationDto {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  version: number;
  messages: CloudMessageDto[];
}

export interface CloudSummaryDto {
  id: string;
  title: string;
  createdAt: number;
  updatedAt: number;
  version: number;
  messageCount: number;
  preview: string;
}

export interface CloudUser {
  username: string;
}

export interface CloudAuthResponse {
  token: string;
  expiresAt: number;
  user: CloudUser;
}

export interface HttpResult {
  status: number;
  body: string;
}
