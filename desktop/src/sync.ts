import { CloudAuthError, CloudClient, CloudError } from "./cloud";
import { state, isLoggedIn, saveNow, updateConversation, removeConversation } from "./store";
import type {
  ChatImage,
  ChatMessage,
  CloudConversationDto,
  CloudProviderDto,
  Conversation,
  MessageStatus,
  Provider,
} from "./types";
import { modelOptions, uid } from "./util";

let inFlight: Promise<void> | null = null;

function client(): CloudClient {
  const account = state.cloudAccount;
  if (!account) throw new CloudAuthError("尚未登录");
  return new CloudClient(account.serverUrl, account.token);
}

function providerToDto(provider: Provider): CloudProviderDto {
  return {
    id: provider.id,
    name: provider.name,
    protocol: provider.protocol,
    baseUrl: provider.baseUrl,
    apiKey: provider.apiKey,
    model: provider.model,
    systemPrompt: provider.systemPrompt,
    temperature: provider.temperature,
    maxTokens: provider.maxTokens,
  };
}

function providerFromDto(dto: CloudProviderDto, existing?: Provider): Provider {
  const options = modelOptions(dto.model ?? "");
  const keep = existing?.selectedModel;
  return {
    id: dto.id,
    name: dto.name || "未命名服务商",
    protocol: dto.protocol === "RESPONSES" ? "RESPONSES" : "CHAT_COMPLETIONS",
    baseUrl: dto.baseUrl || "",
    apiKey: dto.apiKey || "",
    model: options.join(", "),
    systemPrompt: dto.systemPrompt || "",
    temperature: typeof dto.temperature === "number" ? dto.temperature : null,
    maxTokens: typeof dto.maxTokens === "number" ? dto.maxTokens : null,
    selectedModel: keep && options.includes(keep) ? keep : (options[0] ?? ""),
    localOnly: false,
    cloudSynced: true,
  };
}

function statusFromRemote(value: string | undefined): MessageStatus {
  const normalized = (value ?? "").toUpperCase();
  if (normalized === "STREAMING") return "STOPPED";
  if (normalized === "STOPPED" || normalized === "ERROR" || normalized === "COMPLETE") return normalized;
  return "COMPLETE";
}

export async function remoteToLocalConversation(dto: CloudConversationDto): Promise<Conversation> {
  const api = client();
  const messages: ChatMessage[] = [];
  for (const message of dto.messages ?? []) {
    const images: ChatImage[] = [];
    for (const image of message.images ?? []) {
      let dataUrl = "";
      if (image.remoteId) {
        try {
          dataUrl = await api.downloadImage(image.remoteId);
        } catch (error) {
          console.warn("download cloud image failed", error);
        }
      }
      images.push({
        id: image.id || uid(),
        dataUrl,
        mimeType: image.mimeType || "image/jpeg",
        width: typeof image.width === "number" ? image.width : 1,
        height: typeof image.height === "number" ? image.height : 1,
        remoteId: image.remoteId ?? null,
      });
    }
    messages.push({
      id: message.id || uid(),
      role: message.role === "user" ? "USER" : "ASSISTANT",
      content: message.content ?? "",
      images,
      createdAt: typeof message.createdAt === "number" ? message.createdAt : Date.now(),
      status: statusFromRemote(message.status),
      error: message.error ?? null,
    });
  }
  return {
    id: dto.id,
    title: dto.title || "云端对话",
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    messages,
    cloud: true,
    cloudVersion: dto.version,
  };
}

function messagePayload(conversation: Conversation) {
  return conversation.messages
    .filter((message) => message.status !== "STREAMING")
    .map((message) => ({
      id: message.id,
      role: message.role === "USER" ? "user" : "assistant",
      content: message.content,
      images: message.images.map((image) => ({
        id: image.id,
        remoteId: image.remoteId ?? null,
        mimeType: image.mimeType,
        width: image.width,
        height: image.height,
      })),
      createdAt: message.createdAt,
      status: message.status,
      error: message.error ?? null,
    }));
}

async function uploadConversationImages(conversation: Conversation): Promise<void> {
  const api = client();
  let changed = false;
  for (const message of conversation.messages) {
    for (const image of message.images) {
      if (!image.remoteId && image.dataUrl) {
        const uploaded = await api.uploadImage(image.dataUrl);
        image.remoteId = uploaded.id;
        changed = true;
      }
    }
  }
  if (changed) {
    state.conversations = state.conversations.map((item) => (item.id === conversation.id ? conversation : item));
    await saveNow();
  }
}

export async function syncConversation(conversationId: string): Promise<void> {
  const account = state.cloudAccount;
  if (!account) return;
  const conversation = state.conversations.find((item) => item.id === conversationId);
  if (!conversation || !conversation.cloud) return;
  if (conversation.messages.some((message) => message.status === "STREAMING")) return;
  const api = new CloudClient(account.serverUrl, account.token);

  // Keep the in-memory object synchronized with new remote IDs.
  const current = state.conversations.find((item) => item.id === conversationId);
  if (!current) return;
  await uploadConversationImages(current);

  const payload = {
    title: current.title,
    createdAt: current.createdAt,
    updatedAt: Date.now(),
    messages: messagePayload(current),
  };
  try {
    let remote: CloudConversationDto;
    if (current.cloudVersion <= 0) {
      remote = await api.createConversation({ id: current.id, ...payload });
    } else {
      remote = await api.updateConversation(current.id, current.cloudVersion, payload);
    }
    const latest = state.conversations.find((item) => item.id === current.id);
    if (latest) {
      latest.cloud = true;
      latest.cloudVersion = remote.version;
      latest.updatedAt = remote.updatedAt;
      updateConversation(latest);
      await saveNow();
    }
  } catch (error) {
    if (error instanceof CloudError && error.status === 409) {
      const remote = await api.getConversation(current.id);
      const converted = await remoteToLocalConversation(remote);
      updateConversation(converted);
      await saveNow();
      return;
    }
    throw error;
  }
}

export async function syncProvider(providerId: string): Promise<void> {
  if (!isLoggedIn()) return;
  const provider = state.providers.find((item) => item.id === providerId);
  if (!provider) return;
  const api = client();
  if (provider.localOnly) {
    try {
      await api.deleteProvider(provider.id);
    } catch (error) {
      if (!(error instanceof CloudError && error.status === 404)) throw error;
    }
    provider.cloudSynced = false;
  } else {
    await api.putProvider(providerToDto(provider));
    provider.cloudSynced = true;
  }
  await saveNow();
}

export async function deleteRemoteProvider(providerId: string): Promise<void> {
  if (!isLoggedIn()) return;
  try {
    await client().deleteProvider(providerId);
  } catch (error) {
    if (!(error instanceof CloudError && error.status === 404)) throw error;
  }
}

export async function deleteRemoteConversation(conversationId: string): Promise<void> {
  if (!isLoggedIn()) return;
  try {
    await client().deleteConversation(conversationId);
  } catch (error) {
    if (!(error instanceof CloudError && error.status === 404)) throw error;
  }
}

async function processPendingDeletes(): Promise<void> {
  if (!isLoggedIn()) return;
  const api = client();
  let changed = false;
  for (const id of [...state.pendingCloudConversationDeletes]) {
    try {
      await api.deleteConversation(id);
      state.pendingCloudConversationDeletes = state.pendingCloudConversationDeletes.filter((item) => item !== id);
      changed = true;
    } catch (error) {
      if (error instanceof CloudError && error.status === 404) {
        state.pendingCloudConversationDeletes = state.pendingCloudConversationDeletes.filter((item) => item !== id);
        changed = true;
      } else {
        throw error;
      }
    }
  }
  for (const id of [...state.pendingCloudProviderDeletes]) {
    try {
      await api.deleteProvider(id);
      state.pendingCloudProviderDeletes = state.pendingCloudProviderDeletes.filter((item) => item !== id);
      changed = true;
    } catch (error) {
      if (error instanceof CloudError && error.status === 404) {
        state.pendingCloudProviderDeletes = state.pendingCloudProviderDeletes.filter((item) => item !== id);
        changed = true;
      } else {
        throw error;
      }
    }
  }
  if (changed) await saveNow();
}

async function syncProviders(): Promise<void> {
  const api = client();
  const remote = await api.listProviders();
  const remoteIds = new Set(remote.map((provider) => provider.id));
  const localById = new Map(state.providers.map((provider) => [provider.id, provider]));

  for (const dto of remote) {
    const local = localById.get(dto.id);
    if (local?.localOnly) continue;
    if (local && !local.cloudSynced) continue;
    localById.set(dto.id, providerFromDto(dto, local));
  }

  for (const [id, provider] of [...localById]) {
    if (!provider.localOnly && provider.cloudSynced && !remoteIds.has(id)) {
      localById.delete(id);
    }
  }

  for (const provider of [...localById.values()]) {
    if (!provider.localOnly && !provider.cloudSynced) {
      try {
        await api.putProvider(providerToDto(provider));
        provider.cloudSynced = true;
      } catch (error) {
        console.warn("provider upload failed", error);
      }
    }
  }

  const merged = [...localById.values()];
  state.providers = merged;
  if (!merged.some((provider) => provider.id === state.activeProviderId)) {
    state.activeProviderId = merged[0]?.id ?? null;
  }
  await saveNow();
}

async function syncConversations(): Promise<void> {
  const api = client();
  const summaries = await api.listConversations();
  const byId = new Map(summaries.map((summary) => [summary.id, summary]));
  const uploads: string[] = [];

  for (const conversation of [...state.conversations]) {
    if (!conversation.cloud) continue;
    const summary = byId.get(conversation.id);
    if (!summary) {
      if (conversation.messages.some((message) => message.status === "STREAMING")) continue;
      if (conversation.cloudVersion > 0) {
        removeConversation(conversation.id);
        await saveNow();
      } else {
        uploads.push(conversation.id);
      }
      continue;
    }
    if (conversation.messages.some((message) => message.status === "STREAMING")) continue;
    if (conversation.cloudVersion <= 0 || conversation.updatedAt > summary.updatedAt) {
      uploads.push(conversation.id);
    } else if (conversation.cloudVersion < summary.version || conversation.updatedAt < summary.updatedAt) {
      const remote = await api.getConversation(summary.id);
      updateConversation(await remoteToLocalConversation(remote));
      await saveNow();
    }
  }

  for (const summary of summaries) {
    if (state.conversations.some((conversation) => conversation.id === summary.id)) continue;
    const remote = await api.getConversation(summary.id);
    updateConversation(await remoteToLocalConversation(remote));
    await saveNow();
  }

  for (const id of uploads) {
    try {
      await syncConversation(id);
    } catch (error) {
      console.warn("conversation upload failed", id, error);
    }
  }
}

async function runSync(): Promise<void> {
  if (!isLoggedIn()) return;
  await processPendingDeletes();
  await syncProviders();
  await syncConversations();
}

export async function syncAll(): Promise<void> {
  if (!isLoggedIn()) return;
  if (inFlight) return inFlight;
  inFlight = runSync().finally(() => {
    inFlight = null;
  });
  return inFlight;
}

export function isSyncing(): boolean {
  return inFlight !== null;
}
