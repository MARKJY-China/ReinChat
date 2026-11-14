import type { ChatImage, Protocol, Provider } from "./types";

export const DEFAULT_CLOUD_SERVER = "https://reinchat.markjy.com";

export function uid(): string {
  if (crypto.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function modelOptions(raw: string): string[] {
  const seen = new Set<string>();
  return raw
    .split(",")
    .map((item) => item.trim())
    .filter((item) => {
      if (!item || seen.has(item)) return false;
      seen.add(item);
      return true;
    });
}

export function activeModel(provider: Provider): string {
  const options = modelOptions(provider.model);
  return options.includes(provider.selectedModel)
    ? provider.selectedModel
    : (options[0] ?? "");
}

export function normalizeApiKey(raw: string): string {
  return Array.from(raw)
    .filter((ch) => ch !== "\u200b" && ch !== "\u200c" && ch !== "\u200d" && ch !== "\u2060" && ch !== "\ufeff" && ch !== "\u00ad")
    .map((ch) => {
      if (ch === "\u00a0" || ch === "\u2007" || ch === "\u202f") return " ";
      if (["\u2010", "\u2011", "\u2012", "\u2013", "\u2014", "\u2015", "\u2212", "\uff0d"].includes(ch)) return "-";
      if (ch === "\uff3f") return "_";
      return ch;
    })
    .filter((ch) => !/\s/u.test(ch))
    .join("");
}

export function apiKeyProblem(key: string): string | null {
  let index = 0;
  for (const ch of key) {
    const code = ch.codePointAt(0) ?? 0;
    if (code < 33 || code > 126) {
      return `API Key 第 ${index + 1} 个字符不可用于 HTTP 请求头（U+${code.toString(16).toUpperCase().padStart(4, "0")}）。请重新复制纯文本 Key，避免全角字符、表情或隐藏符号。`;
    }
    index += 1;
  }
  return null;
}

export function normalizeEndpoint(raw: string): string {
  let value = raw.trim();
  if (!value) return "";
  if (!/^https?:\/\//i.test(value)) value = `https://${value}`;
  return value.replace(/\/+$/, "");
}

export function cloudEndpoint(raw: string): string {
  let value = normalizeEndpoint(raw);
  if (!value) value = DEFAULT_CLOUD_SERVER;
  if (/\/api\/v1$/i.test(value)) return value;
  return `${value}/api/v1`;
}

export function endpointUrl(baseUrl: string, protocol: Protocol): string {
  const base = normalizeEndpoint(baseUrl);
  if (!base) throw new Error("请填写 Base URL");
  if (protocol === "RESPONSES") {
    return /\/responses$/i.test(base) ? base : `${base}/responses`;
  }
  return /\/chat\/completions$/i.test(base) ? base : `${base}/chat/completions`;
}

export function baseUrlProblem(raw: string): string | null {
  const value = normalizeEndpoint(raw);
  if (!value) return "请填写 Base URL";
  try {
    const url = new URL(value);
    if (url.protocol !== "http:" && url.protocol !== "https:") return "Base URL 必须以 http:// 或 https:// 开头";
    if (!url.hostname) return "Base URL 缺少主机名";
    return null;
  } catch {
    return "Base URL 格式不正确";
  }
}

export function escapeHtml(raw: string): string {
  return raw
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

export function titleFromPrompt(text: string, imageCount: number): string {
  const compact = text.replace(/\s+/g, " ").trim();
  if (!compact) return imageCount > 0 ? "图片对话" : "新对话";
  return compact.length <= 30 ? compact : `${compact.slice(0, 29).trimEnd()}…`;
}

export function clock(ts: number): string {
  return new Date(ts).toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" });
}

export function shortDate(ts: number): string {
  const date = new Date(ts);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return clock(ts);
  return date.toLocaleDateString("zh-CN", { month: "2-digit", day: "2-digit" });
}

export function hostOf(url: string): string {
  try {
    return new URL(url).host || url;
  } catch {
    return url;
  }
}

export function serverLabel(url: string): string {
  try {
    const parsed = new URL(url);
    return `${parsed.protocol}//${parsed.host}`;
  } catch {
    return url;
  }
}

export function deepClone<T>(value: T): T {
  return structuredClone(value);
}

export interface ImportedImage {
  image: ChatImage;
  originalBytes: number;
}

export async function importImageFile(file: File): Promise<ImportedImage> {
  if (!file.type.startsWith("image/")) throw new Error("只能添加图片文件");
  if (file.size > 28 * 1024 * 1024) throw new Error("单张图片不能超过 28 MB");
  const bitmap = await createImageBitmap(file);
  const maxSide = 1600;
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  const width = Math.max(1, Math.round(bitmap.width * scale));
  const height = Math.max(1, Math.round(bitmap.height * scale));
  const canvas = document.createElement("canvas");
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("无法处理图片");
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close?.();
  const dataUrl = canvas.toDataURL("image/jpeg", 0.86);
  return {
    image: {
      id: uid(),
      dataUrl,
      mimeType: "image/jpeg",
      width,
      height,
      remoteId: null,
    },
    originalBytes: file.size,
  };
}

export function messagePreview(content: string, fallback: string): string {
  const compact = content.replace(/\s+/g, " ").trim();
  return compact ? (compact.length > 50 ? `${compact.slice(0, 50)}…` : compact) : fallback;
}

export function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
