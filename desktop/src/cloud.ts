import { invoke } from "@tauri-apps/api/core";
import type {
  CloudAuthResponse,
  CloudConversationDto,
  CloudProviderDto,
  CloudSummaryDto,
  HttpResult,
} from "./types";
import { cloudEndpoint, normalizeEndpoint } from "./util";

export class CloudError extends Error {
  status: number;
  code: string | null;
  constructor(message: string, status = 0, code: string | null = null) {
    super(message);
    this.name = "CloudError";
    this.status = status;
    this.code = code;
  }
}

export class CloudAuthError extends CloudError {
  constructor(message = "登录已过期，请重新登录") {
    super(message, 401, "unauthorized");
    this.name = "CloudAuthError";
  }
}

interface ErrorEnvelope {
  error?: { code?: string; message?: string };
}

function messageFromBody(raw: string): string {
  if (!raw.trim()) return "服务器返回了空响应";
  try {
    const parsed = JSON.parse(raw) as ErrorEnvelope;
    const message = parsed.error?.message;
    if (typeof message === "string" && message.trim()) return message;
  } catch {
    // Fall back to a short plain-text error.
  }
  return raw.replace(/<[^>]+>/g, " ").replace(/\s+/g, " ").trim().slice(0, 240) || "请求失败";
}

export class CloudClient {
  readonly baseUrl: string;
  private readonly token: string;

  constructor(serverUrl: string, token = "") {
    this.baseUrl = cloudEndpoint(serverUrl);
    this.token = token;
  }

  static normalizeServer(raw: string): string {
    const endpoint = cloudEndpoint(raw);
    return endpoint.replace(/\/api\/v1$/i, "");
  }

  private async request(method: string, path: string, body?: unknown, token = this.token): Promise<HttpResult> {
    const result = await invoke<HttpResult>("cloud_request", {
      request: {
        method,
        url: `${this.baseUrl}${path}`,
        token: token || null,
        body: body ?? null,
      },
    });
    if (result.status < 200 || result.status >= 300) {
      const message = messageFromBody(result.body);
      if (result.status === 401 || result.status === 403) throw new CloudAuthError(message);
      let code: string | null = null;
      try {
        code = (JSON.parse(result.body) as ErrorEnvelope).error?.code ?? null;
      } catch {
        code = null;
      }
      throw new CloudError(message, result.status, code);
    }
    return result;
  }

  private async json<T>(method: string, path: string, body?: unknown): Promise<T> {
    const result = await this.request(method, path, body);
    if (!result.body.trim()) return undefined as T;
    try {
      return JSON.parse(result.body) as T;
    } catch {
      throw new CloudError("服务器返回了无法解析的数据", result.status);
    }
  }

  async register(username: string, password: string): Promise<CloudAuthResponse> {
    this.assertPublicCredentials(username, password);
    return this.json<CloudAuthResponse>("POST", "/auth/register", { username, password });
  }

  async login(username: string, password: string): Promise<CloudAuthResponse> {
    this.assertPublicCredentials(username, password);
    return this.json<CloudAuthResponse>("POST", "/auth/login", { username, password });
  }

  private assertPublicCredentials(username: string, password: string) {
    if (username.trim().length < 3) throw new CloudError("账号至少需要 3 个字符", 0);
    if (password.length < 8) throw new CloudError("密码至少需要 8 个字符", 0);
  }

  async me(): Promise<{ user: { username: string } }> {
    return this.json("GET", "/auth/me");
  }

  async logout(): Promise<void> {
    await this.request("POST", "/auth/logout");
  }

  async listProviders(): Promise<CloudProviderDto[]> {
    const result = await this.json<{ providers: CloudProviderDto[] }>("GET", "/providers");
    return result.providers ?? [];
  }

  async putProvider(provider: CloudProviderDto): Promise<void> {
    await this.request("PUT", `/providers/${encodeURIComponent(provider.id)}`, provider);
  }

  async deleteProvider(id: string): Promise<void> {
    await this.request("DELETE", `/providers/${encodeURIComponent(id)}`);
  }

  async listConversations(): Promise<CloudSummaryDto[]> {
    const result = await this.json<{ conversations: CloudSummaryDto[] }>("GET", "/conversations");
    return result.conversations ?? [];
  }

  async getConversation(id: string): Promise<CloudConversationDto> {
    return this.json<CloudConversationDto>("GET", `/conversations/${encodeURIComponent(id)}`);
  }

  async createConversation(payload: {
    id: string;
    title: string;
    createdAt: number;
    updatedAt: number;
    messages: CloudConversationDto["messages"];
  }): Promise<CloudConversationDto> {
    return this.json<CloudConversationDto>("POST", "/conversations", payload);
  }

  async updateConversation(
    id: string,
    baseVersion: number,
    payload: {
      title: string;
      createdAt: number;
      updatedAt: number;
      messages: CloudConversationDto["messages"];
    },
  ): Promise<CloudConversationDto> {
    return this.json<CloudConversationDto>("PUT", `/conversations/${encodeURIComponent(id)}`, {
      baseVersion,
      ...payload,
    });
  }

  async deleteConversation(id: string): Promise<void> {
    await this.request("DELETE", `/conversations/${encodeURIComponent(id)}`);
  }

  async uploadImage(dataUrl: string): Promise<{ id: string; mimeType: string; size: number }> {
    const result = await invoke<HttpResult>("cloud_upload_image", {
      request: {
        url: `${this.baseUrl}/files`,
        token: this.token,
        dataUrl,
      },
    });
    if (result.status < 200 || result.status >= 300) {
      throw new CloudError(messageFromBody(result.body), result.status);
    }
    try {
      return JSON.parse(result.body) as { id: string; mimeType: string; size: number };
    } catch {
      throw new CloudError("图片上传响应无法解析", result.status);
    }
  }

  async downloadImage(remoteId: string): Promise<string> {
    try {
      return await invoke<string>("cloud_download_image", {
        request: {
          url: `${this.baseUrl}/files/${encodeURIComponent(remoteId)}`,
          token: this.token,
        },
      });
    } catch (error) {
      throw new CloudError(error instanceof Error ? error.message : String(error));
    }
  }

  get rawServer(): string {
    return this.baseUrl;
  }
}

export function serverHost(raw: string): string {
  return normalizeEndpoint(raw).replace(/\/api\/v1$/i, "");
}
