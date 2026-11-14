import { invoke } from "@tauri-apps/api/core";
import type { AppState, ChatImage, ChatMessage, Conversation, MessageStatus, Provider, Protocol } from "./types";
import { DEFAULT_CLOUD_SERVER, activeModel, modelOptions, uid } from "./util";

export const state: AppState = {
  version: 1,
  providers: [],
  activeProviderId: null,
  conversations: [],
  activeConversationId: null,
  cloudAccount: null,
  cloudServerUrl: DEFAULT_CLOUD_SERVER,
  newChatCloud: false,
  pendingCloudConversationDeletes: [],
  pendingCloudProviderDeletes: [],
};

function normalizeProtocol(value: unknown): Protocol {
  return value === "RESPONSES" ? "RESPONSES" : "CHAT_COMPLETIONS";
}

function normalizeStatus(value: unknown): MessageStatus {
  if (value === "STREAMING") return "STOPPED";
  if (value === "STOPPED" || value === "ERROR" || value === "COMPLETE") return value;
  return "COMPLETE";
}

function normalizeImage(raw: unknown): ChatImage | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;
  if (typeof item.dataUrl !== "string" || !item.dataUrl) return null;
  return {
    id: typeof item.id === "string" ? item.id : uid(),
    dataUrl: item.dataUrl,
    mimeType: typeof item.mimeType === "string" ? item.mimeType : "image/jpeg",
    width: typeof item.width === "number" ? item.width : 1,
    height: typeof item.height === "number" ? item.height : 1,
    remoteId: typeof item.remoteId === "string" ? item.remoteId : null,
  };
}

function normalizeMessage(raw: unknown): ChatMessage | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;
  if (item.role !== "USER" && item.role !== "ASSISTANT") return null;
  const images = Array.isArray(item.images)
    ? item.images.map(normalizeImage).filter((image): image is ChatImage => image !== null)
    : [];
  return {
    id: typeof item.id === "string" ? item.id : uid(),
    role: item.role,
    content: typeof item.content === "string" ? item.content : "",
    images,
    createdAt: typeof item.createdAt === "number" ? item.createdAt : Date.now(),
    status: normalizeStatus(item.status),
    error: typeof item.error === "string" ? item.error : null,
  };
}

function normalizeProvider(raw: unknown): Provider | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;
  const model = typeof item.model === "string" ? item.model : "";
  const options = modelOptions(model);
  const selected = typeof item.selectedModel === "string" ? item.selectedModel : "";
  return {
    id: typeof item.id === "string" ? item.id : uid(),
    name: typeof item.name === "string" && item.name.trim() ? item.name : "未命名服务商",
    protocol: normalizeProtocol(item.protocol),
    baseUrl: typeof item.baseUrl === "string" ? item.baseUrl : "",
    apiKey: typeof item.apiKey === "string" ? item.apiKey : "",
    model: options.join(", "),
    systemPrompt: typeof item.systemPrompt === "string" ? item.systemPrompt : "",
    temperature: typeof item.temperature === "number" ? item.temperature : null,
    maxTokens: typeof item.maxTokens === "number" ? item.maxTokens : null,
    selectedModel: options.includes(selected) ? selected : (options[0] ?? ""),
    localOnly: item.localOnly !== false,
    cloudSynced: item.cloudSynced === true,
  };
}

function normalizeConversation(raw: unknown): Conversation | null {
  if (!raw || typeof raw !== "object") return null;
  const item = raw as Record<string, unknown>;
  const messages = Array.isArray(item.messages)
    ? item.messages.map(normalizeMessage).filter((message): message is ChatMessage => message !== null)
    : [];
  return {
    id: typeof item.id === "string" ? item.id : uid(),
    title: typeof item.title === "string" && item.title.trim() ? item.title : "新对话",
    createdAt: typeof item.createdAt === "number" ? item.createdAt : Date.now(),
    updatedAt: typeof item.updatedAt === "number" ? item.updatedAt : Date.now(),
    messages,
    cloud: item.cloud === true,
    cloudVersion: typeof item.cloudVersion === "number" ? item.cloudVersion : 0,
  };
}

export function normalizeLoadedState(raw: unknown): AppState {
  if (!raw || typeof raw !== "object") return state;
  const item = raw as Record<string, unknown>;
  const providers = Array.isArray(item.providers)
    ? item.providers.map(normalizeProvider).filter((provider): provider is Provider => provider !== null)
    : [];
  const account = item.cloudAccount && typeof item.cloudAccount === "object"
    ? (item.cloudAccount as AppState["cloudAccount"])
    : null;
  const conversations = Array.isArray(item.conversations)
    ? item.conversations
        .map(normalizeConversation)
        .filter((conversation): conversation is Conversation => conversation !== null)
        .filter((conversation) => !conversation.cloud || account !== null)
    : [];
  const activeProviderId = typeof item.activeProviderId === "string" ? item.activeProviderId : null;
  const activeConversationId = typeof item.activeConversationId === "string" ? item.activeConversationId : null;
  return {
    version: 1,
    providers,
    activeProviderId: providers.some((provider) => provider.id === activeProviderId)
      ? activeProviderId
      : (providers[0]?.id ?? null),
    conversations: conversations.sort((a, b) => b.updatedAt - a.updatedAt),
    activeConversationId: conversations.some((conversation) => conversation.id === activeConversationId)
      ? activeConversationId
      : null,
    cloudAccount: account,
    cloudServerUrl: typeof item.cloudServerUrl === "string" && item.cloudServerUrl.trim()
      ? item.cloudServerUrl
      : DEFAULT_CLOUD_SERVER,
    newChatCloud: account !== null && item.newChatCloud !== false,
    pendingCloudConversationDeletes: Array.isArray(item.pendingCloudConversationDeletes)
      ? item.pendingCloudConversationDeletes.filter((value): value is string => typeof value === "string")
      : [],
    pendingCloudProviderDeletes: Array.isArray(item.pendingCloudProviderDeletes)
      ? item.pendingCloudProviderDeletes.filter((value): value is string => typeof value === "string")
      : [],
  };
}

export async function loadState(): Promise<void> {
  const raw = await invoke<string | null>("load_state");
  if (!raw) {
    state.newChatCloud = false;
    return;
  }
  try {
    Object.assign(state, normalizeLoadedState(JSON.parse(raw)));
  } catch (error) {
    console.error("load state failed", error);
  }
}

let saveTimer: number | null = null;
let saving: Promise<void> | null = null;

export function scheduleSave(): void {
  if (saveTimer !== null) window.clearTimeout(saveTimer);
  saveTimer = window.setTimeout(() => {
    saveTimer = null;
    void saveNow();
  }, 240);
}

export async function saveNow(): Promise<void> {
  if (saveTimer !== null) {
    window.clearTimeout(saveTimer);
    saveTimer = null;
  }
  if (saving) await saving;
  const payload = JSON.stringify(state);
  saving = invoke("save_state", { contents: payload })
    .then(() => undefined)
    .catch((error) => {
      console.error("save state failed", error);
    })
    .finally(() => {
      saving = null;
    });
  await saving;
}

export function activeProvider(): Provider | null {
  return state.providers.find((provider) => provider.id === state.activeProviderId) ?? state.providers[0] ?? null;
}

export function activeConversation(): Conversation | null {
  return state.conversations.find((conversation) => conversation.id === state.activeConversationId) ?? null;
}

export function isLoggedIn(): boolean {
  return state.cloudAccount !== null && state.cloudAccount.token.length > 0;
}

export function ensureProviderSelection(provider: Provider): Provider {
  const options = modelOptions(provider.model);
  return {
    ...provider,
    model: options.join(", "),
    selectedModel: options.includes(provider.selectedModel) ? provider.selectedModel : (options[0] ?? ""),
  };
}

export function updateConversation(conversation: Conversation): void {
  const index = state.conversations.findIndex((item) => item.id === conversation.id);
  if (index >= 0) state.conversations[index] = conversation;
  else state.conversations.unshift(conversation);
  state.conversations.sort((a, b) => b.updatedAt - a.updatedAt);
}

export function removeConversation(id: string): void {
  state.conversations = state.conversations.filter((conversation) => conversation.id !== id);
  if (state.activeConversationId === id) state.activeConversationId = null;
}

export function createProvider(): Provider {
  return {
    id: uid(),
    name: "新的服务商",
    protocol: "CHAT_COMPLETIONS",
    baseUrl: "https://api.openai.com/v1",
    apiKey: "",
    model: "gpt-4o-mini",
    systemPrompt: "",
    temperature: null,
    maxTokens: null,
    selectedModel: "gpt-4o-mini",
    localOnly: true,
    cloudSynced: false,
  };
}

export function ensureActiveModel(provider: Provider): string {
  return activeModel(provider);
}
