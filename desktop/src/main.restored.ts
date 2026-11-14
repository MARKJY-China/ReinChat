import "./styles.css";
import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { openUrl } from "@tauri-apps/plugin-opener";
import type { ChatImage, ChatMessage, Conversation, MessageStatus, Provider } from "./types";
import {
  activeConversation,
  activeProvider,
  isLoggedIn,
  loadState,
  removeConversation,
  saveNow,
  scheduleSave,
  state,
  updateConversation,
} from "./store";
import {
  DEFAULT_CLOUD_SERVER,
  activeModel,
  apiKeyProblem,
  baseUrlProblem,
  clock,
  deepClone,
  endpointUrl,
  escapeHtml,
  hostOf,
  importImageFile,
  messagePreview,
  modelOptions,
  normalizeApiKey,
  shortDate,
  titleFromPrompt,
  uid,
} from "./util";
import { renderMarkdown } from "./markdown";
import { CloudAuthError, CloudClient, CloudError } from "./cloud";
import {
  deleteRemoteConversation,
  deleteRemoteProvider,
  isSyncing,
  remoteToLocalConversation,
  syncAll,
  syncConversation,
  syncProvider,
} from "./sync";

const MAX_IMAGES = 4;

interface StreamRuntime {
  requestId: string;
  conversationId: string;
  messageId: string;
}

interface Runtime {
  sidebarOpen: boolean;
  search: string;
  draft: string;
  attachments: ChatImage[];
  editing: { conversationId: string; messageId: string } | null;
  actionsOpen: string | null;
  rewindUndo: { conversation: Conversation; activeConversationId: string | null } | null;
  streaming: StreamRuntime | null;
  overlay: "settings" | "login" | "provider" | "provider-picker" | null;
  providerDraft: Provider | null;
  providerIsNew: boolean;
  showProviderKey: boolean;
  loginMode: "login" | "register";
  loginBusy: boolean;
  loginError: string;
  loginServer: string;
  loginUsername: string;
  loginPassword: string;
  cloudBusy: boolean;
  syncError: string;
  lastSync: number;
  viewerImage: ChatImage | null;
  importing: boolean;
  modelPickerProviderId: string | null;
}

const runtime: Runtime = {
  sidebarOpen: window.innerWidth > 820,
  search: "",
  draft: "",
  attachments: [],
  editing: null,
  actionsOpen: null,
  rewindUndo: null,
  streaming: null,
  overlay: null,
  providerDraft: null,
  providerIsNew: false,
  showProviderKey: false,
  loginMode: "login",
  loginBusy: false,
  loginError: "",
  loginServer: state.cloudServerUrl,
  loginUsername: "",
  loginPassword: "",
  cloudBusy: false,
  syncError: "",
  lastSync: 0,
  viewerImage: null,
  importing: false,
  modelPickerProviderId: null,
};

const ICONS: Record<string, string> = {
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m16.5 16.5 4 4"/>',
  settings: '<path d="M4 7h9M17 7h3M4 17h3M11 17h9"/><circle cx="15" cy="7" r="2"/><circle cx="9" cy="17" r="2"/>',
  cloud: '<path d="M7.5 18.5h9.2a3.8 3.8 0 0 0 .5-7.56A5.6 5.6 0 0 0 6.5 9.7 4.4 4.4 0 0 0 7.5 18.5Z"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="9" cy="10" r="1.6"/><path d="m4 17 5-5 4 4 2-2 5 5"/>',
  send: '<path d="M12 19V5M6 11l6-6 6 6"/>',
  close: '<path d="m6 6 12 12M18 6 6 18"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3M6.5 7l.8 13h9.4l.8-13"/>',
  edit: '<path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17v3ZM13.5 7.5l3 3"/>',
  copy: '<rect x="8" y="8" width="11" height="12" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h2"/>',
  refresh: '<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 9A7 7 0 0 1 18.6 7M17.9 15A7 7 0 0 1 5.4 17"/>',
  replay: '<path d="M4 10h6V4"/><path d="M4.6 15a8 8 0 1 0 1.8-8.3L4 10"/>',
  check: '<path d="m5 12 4.5 4.5L19 7"/>',
  lock: '<rect x="5" y="10" width="14" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  arrow: '<path d="M9 18l6-6-6-6"/>',
  dots: '<circle cx="6" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="18" cy="12" r="1"/>',
  alert: '<path d="M12 9v4M12 17h.01"/><circle cx="12" cy="12" r="9"/>',
  external: '<path d="M14 4h6v6M20 4l-9 9"/><path d="M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
};

function icon(name: string, size = 16): string {
  return `<svg aria-hidden="true" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${ICONS[name] ?? ICONS.dots}</svg>`;
}

function $(id: string): HTMLElement {
  const node = document.getElementById(id);
  if (!node) throw new Error(`missing element #${id}`);
  return node;
}

function esc(value: unknown): string {
  return escapeHtml(String(value ?? ""));
}

const root = $("#app");
const overlayRoot = $("#overlay-root");
const toastRoot = $("#toast-root");
const imageRoot = $("#image-viewer-root");

function buildShell(): void {
  root.innerHTML = `
    <aside class="sidebar" id="sidebar">
      <div class="sidebar-head">
        <div class="brand-orb">R</div>
        <div class="brand-copy">
          <div class="brand-name">ReinChat</div>
          <div class="brand-sub">驭聊 · Windows</div>
        </div>
      </div>
      <div class="sidebar-actions">
        <button class="new-chat" data-action="new-chat"><span class="plus">+</span><span>新建对话</span></button>
      </div>
      <div class="search-box">
        ${icon("search", 13)}
        <input id="search-input" placeholder="搜索会话或消息" autocomplete="off" />
      </div>
      <div class="sidebar-label"><span>最近会话</span><span id="conv-count">0</span></div>
      <div class="conversation-scroll" id="conversation-list"></div>
      <div class="sidebar-footer">
        <div id="account-card"></div>
        <button class="settings-link" data-action="open-settings">
          ${icon("settings", 14)}<span>服务商与设置</span><span class="version">v0.3</span>
        </button>
      </div>
    </aside>
    <main class="main">
      <header class="chat-header">
        <button class="header-icon" data-action="toggle-sidebar" title="收起 / 展开侧栏">${icon("menu", 17)}</button>
        <div class="header-copy">
          <h1 class="chat-title" id="chat-title">ReinChat</h1>
          <div class="chat-subtitle" id="chat-subtitle">驭聊 · 一个安静的 AI 对话空间</div>
        </div>
        <div class="header-actions">
          <span id="header-cloud"></span>
          <button class="header-icon" data-action="new-chat" title="新建对话">${icon("plus", 18)}</button>
          <button class="header-icon" data-action="open-settings" title="设置">${icon("settings", 17)}</button>
        </div>
      </header>
      <section class="chat-scroll" id="chat-scroll">
        <div class="chat-content" id="chat-content"></div>
      </section>
      <footer class="composer-wrap">
        <div class="composer">
          <div class="edit-banner" id="edit-banner">
            <span>正在编辑之前的提问</span>
            <button data-action="cancel-edit">取消</button>
          </div>
          <div class="attachments" id="attachments"></div>
          <textarea class="draft" id="draft" rows="1" placeholder="给 ReinChat 发消息…" spellcheck="false"></textarea>
          <div class="composer-bar">
            <button class="attach-button" data-action="attach-image" title="添加图片">${icon("image", 16)}</button>
            <button class="provider-chip" id="provider-chip" data-action="open-provider-picker"></button>
            <span class="direct-label">端侧直连</span>
            <button class="send-button" id="send-button" data-action="send" title="发送">${icon("send", 17)}</button>
            <button class="stop-button hidden" id="stop-button" data-action="stop" title="停止生成"><span class="stop-square"></span></button>
          </div>
        </div>
        <input id="file-input" type="file" accept="image/*" multiple hidden />
      </footer>
    </main>
  `;

  document.body.classList.toggle("sidebar-collapsed", !runtime.sidebarOpen);
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  draft?.addEventListener("input", () => {
    runtime.draft = draft.value;
    autoResizeDraft();
    updateSendState();
  });
  draft?.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      void sendMessage();
    }
  });
  draft?.addEventListener("paste", (event) => {
    const files = Array.from(event.clipboardData?.files ?? []).filter((file) => file.type.startsWith("image/"));
    if (files.length) {
      event.preventDefault();
      void addImageFiles(files);
    }
  });
  const search = document.getElementById("search-input") as HTMLInputElement | null;
  search?.addEventListener("input", () => {
    runtime.search = search.value;
    renderConversations();
  });
  const fileInput = document.getElementById("file-input") as HTMLInputElement | null;
  fileInput?.addEventListener("change", () => {
    if (fileInput.files?.length) void addImageFiles(Array.from(fileInput.files));
    fileInput.value = "";
  });
}

function buildPayload(provider: Provider, messages: ChatMessage[]): Record<string, unknown> {
  const model = activeModel(provider);
  if (!model) throw new Error("请填写模型名称");
  const relevant = messages.filter((message) => message.content.trim() || message.images.length > 0);
  if (!relevant.length) throw new Error("没有可发送的消息");
  const body: Record<string, unknown> = { model, stream: true };
  if (provider.temperature !== null) body.temperature = provider.temperature;

  if (provider.protocol === "RESPONSES") {
    if (provider.systemPrompt.trim()) body.instructions = provider.systemPrompt.trim();
    body.input = relevant.map((message) => {
      if (message.role === "USER" && message.images.length) {
        const content: unknown[] = [];
        if (message.content.trim()) content.push({ type: "input_text", text: message.content });
        for (const image of message.images) {
          content.push({ type: "input_image", image_url: image.dataUrl });
        }
        return { role: "user", content };
      }
      return { role: message.role === "USER" ? "user" : "assistant", content: message.content };
    });
    if (provider.maxTokens !== null) body.max_output_tokens = provider.maxTokens;
  } else {
    const payloadMessages: unknown[] = [];
    if (provider.systemPrompt.trim()) payloadMessages.push({ role: "system", content: provider.systemPrompt.trim() });
    for (const message of relevant) {
      if (message.role === "USER" && message.images.length) {
        const content: unknown[] = [];
        if (message.content.trim()) content.push({ type: "text", text: message.content });
        for (const image of message.images) {
          content.push({ type: "image_url", image_url: { url: image.dataUrl } });
        }
        payloadMessages.push({ role: "user", content });
      } else {
        payloadMessages.push({
          role: message.role === "USER" ? "user" : "assistant",
          content: message.content,
        });
      }
    }
    body.messages = payloadMessages;
    if (provider.maxTokens !== null) body.max_tokens = provider.maxTokens;
  }
  return body;
}

function senderInitial(name: string): string {
  return Array.from(name.trim())[0]?.toUpperCase() ?? "H";
}

function renderAccountCard(): void {
  const card = document.getElementById("account-card");
  if (!card) return;
  if (!state.cloudAccount) {
    card.innerHTML = `
      <div class="account-card">
        <div class="account-status">登录后，云端会话与服务商配置可跨设备同步。</div>
        <button class="login-cta" data-action="open-login">登录 / 注册</button>
      </div>
    `;
    return;
  }
  const account = state.cloudAccount;
  const syncing = runtime.cloudBusy || isSyncing();
  const status = syncing
    ? "正在同步云端数据…"
    : runtime.syncError
      ? runtime.syncError
      : runtime.lastSync
        ? `已同步 ${clock(runtime.lastSync)}`
        : "多端同步已开启";
  card.innerHTML = `
    <div class="account-card">
      <div class="account-row">
        <div class="account-avatar">${esc(senderInitial(account.username))}</div>
        <div style="min-width:0;flex:1">
          <div class="account-name">${esc(account.username)}</div>
          <div class="account-host">${esc(hostOf(account.serverUrl))}</div>
        </div>
      </div>
      <div class="account-status ${runtime.syncError ? "error" : ""}">${esc(status)}</div>
      <div class="account-actions">
        <button class="text-btn" data-action="sync-cloud">${syncing ? "同步中…" : "立即同步"}</button>
        <button class="text-btn" data-action="logout">退出登录</button>
      </div>
    </div>
  `;
}

function renderConversations(): void {
  const list = document.getElementById("conversation-list");
  if (!list) return;
  const query = runtime.search.trim().toLowerCase();
  const conversations = state.conversations.filter((conversation) => {
    if (!query) return true;
    return (
      conversation.title.toLowerCase().includes(query) ||
      conversation.messages.some((message) => message.content.toLowerCase().includes(query))
    );
  });
  const count = document.getElementById("conv-count");
  if (count) count.textContent = String(state.conversations.length);
  if (!conversations.length) {
    list.innerHTML = `<div style="padding:18px 12px;color:var(--text-3);font-size:10.5px;line-height:1.6">${query ? "没有匹配的会话" : "还没有对话，开始第一段思绪吧。"}</div>`;
    return;
  }
  list.innerHTML = conversations.map((conversation) => {
    const last = conversation.messages[conversation.messages.length - 1];
    const preview = last ? messagePreview(last.content, "图片对话") : "空对话";
    return `
      <div class="conversation-item ${conversation.id === state.activeConversationId ? "active" : ""}" data-action="select-conversation" data-id="${conversation.id}">
        <div class="conversation-dot"></div>
        <div class="conversation-main">
          <div class="conversation-title">
            ${conversation.cloud ? `<span class="cloud-mini">${icon("cloud", 12)}</span>` : ""}
            <span>${esc(conversation.title)}</span>
          </div>
          <div class="conversation-preview">${esc(preview)}</div>
        </div>
        <div class="conversation-time">${esc(shortDate(conversation.updatedAt))}</div>
        <div class="conversation-menu">
          <button class="icon-mini" data-action="rename-conversation" data-id="${conversation.id}" title="重命名">${icon("edit", 12)}</button>
          <button class="icon-mini" data-action="delete-conversation" data-id="${conversation.id}" title="删除">${icon("trash", 12)}</button>
        </div>
      </div>
    `;
  }).join("");
}

function renderHeader(): void {
  const conversation = activeConversation();
  const provider = activeProvider();
  const title = document.getElementById("chat-title");
  const subtitle = document.getElementById("chat-subtitle");
  const cloud = document.getElementById("header-cloud");
  if (title) title.textContent = conversation?.title ?? "ReinChat";
  if (subtitle) {
    if (!provider) {
      subtitle.textContent = "驭聊 · 一个安静的 AI 对话空间";
    } else {
      subtitle.textContent = `${provider.name}  ·  ${activeModel(provider) || "未设置模型"}${conversation ? (conversation.cloud ? "  ·  云端" : "  ·  仅本地") : ""}`;
    }
  }
  if (cloud) {
    cloud.innerHTML = conversation
      ? `<span class="cloud-badge ${conversation.cloud ? "" : "local"}">${conversation.cloud ? icon("cloud", 11) : ""}${conversation.cloud ? "云端" : "仅本地"}</span>`
      : "";
  }
}

function actionPill(label: string, action: string, iconName: string, id: string, extra = ""): string {
  return `<button class="action-pill ${extra}" data-action="${action}" data-id="${id}">${icon(iconName, 12)}<span>${label}</span></button>`;
}

function renderMessage(message: ChatMessage, index: number, conversation: Conversation): string {
  const canRewind = !runtime.streaming && index < conversation.messages.length - 1;
  const actions: string[] = [];
  if (message.status === "ERROR" && message.role === "ASSISTANT") {
    actions.push(actionPill("重试", "retry-message", "refresh", message.id));
  }
  if (message.content.trim()) {
    actions.push(actionPill("复制", "copy-message", "copy", message.id));
  }
  if (message.role === "USER") {
    actions.push(actionPill("编辑", "edit-message", "edit", message.id));
  }
  if (canRewind) {
    actions.push(actionPill("回退到此处", "rewind-message", "replay", message.id));
  }
  const actionsHtml = runtime.actionsOpen === message.id
    ? `<div class="message-actions">${actions.join("")}</div>`
    : "";

  if (message.role === "USER") {
    const imageHtml = message.images.length
      ? `<div class="user-images count-${Math.min(4, message.images.length)}">${message.images.map((image, imageIndex) => image.dataUrl
          ? `<img src="${image.dataUrl}" alt="对话图片" data-action="view-image" data-id="${message.id}" data-image-index="${imageIndex}" />`
          : `<div style="height:120px;border-radius:16px;background:rgba(255,255,255,.04)"></div>`).join("")}</div>`
      : "";
    return `
      <div class="message user" data-message-id="${message.id}">
        <div class="user-bubble" data-message-toggle="${message.id}">
          ${imageHtml}
          ${message.content.trim() ? `<div class="user-text">${esc(message.content)}</div>` : ""}
        </div>
        <div class="message-meta user">
          <span>${clock(message.createdAt)}</span>
          <button class="menu-button" data-action="toggle-message" data-id="${message.id}" title="消息操作">•••</button>
        </div>
        ${actionsHtml}
      </div>
    `;
  }

  const hasContent = message.content.trim().length > 0;
  const body = hasContent
    ? `<div class="md-wrap ${message.status === "STREAMING" ? "streaming-cursor" : ""}">${renderMarkdown(message.content)}</div>`
    : message.status === "STREAMING"
      ? `<div style="display:flex;align-items:center;gap:9px;color:var(--text-3);font-size:12px"><span class="stream-dots"><i></i><i></i><i></i></span>正在思考</div>`
      : "";
  const error = message.status === "ERROR"
    ? `<div class="error-notice">${icon("alert", 14)}<span>${esc(message.error || "生成失败，请稍后重试")}</span></div>`
    : "";
  return `
    <div class="message assistant" data-message-id="${message.id}">
      <div class="assistant" data-message-toggle="${message.id}">
        <div class="assistant-head">
          <div class="assistant-orb">R</div>
          <div class="assistant-name">ReinChat</div>
          ${message.status === "STREAMING" ? `<span class="stream-dots"><i></i><i></i><i></i></span>` : ""}
        </div>
        ${body}
        ${error}
      </div>
      <div class="message-meta">
        <span>${clock(message.createdAt)}</span>
        ${message.status === "STOPPED" ? `<span>· 已停止</span>` : ""}
        <button class="menu-button" data-action="toggle-message" data-id="${message.id}" title="消息操作">•••</button>
      </div>
      ${actionsHtml}
    </div>
  `;
}

function isAtBottom(): boolean {
  const scroll = $("chat-scroll");
  return scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 90;
}

function scrollToBottom(): void {
  const scroll = $("chat-scroll");
  scroll.scrollTop = scroll.scrollHeight;
}

function renderChat(forceBottom = false): void {
  const content = $("chat-content");
  const conversation = activeConversation();
  const stick = forceBottom || isAtBottom();
  if (!conversation || conversation.messages.length === 0) {
    content.classList.add("empty-mode");
    content.innerHTML = renderEmpty();
    if (forceBottom) scrollToBottom();
    return;
  }
  content.classList.remove("empty-mode");
  content.innerHTML = `<div class="message-list">${conversation.messages.map((message, index) => renderMessage(message, index, conversation)).join("")}</div>`;
  if (stick) scrollToBottom();
}

let messageFrame: number | null = null;
function renderChatSoon(): void {
  if (messageFrame !== null) return;
  messageFrame = window.requestAnimationFrame(() => {
    messageFrame = null;
    renderChat(true);
  });
}

function renderEmpty(): string {
  const provider = activeProvider();
  const loggedIn = isLoggedIn();
  const cloudOn = loggedIn && state.newChatCloud;
  const cloud = `
    <div class="cloud-toggle ${cloudOn ? "on" : ""} ${loggedIn ? "" : "guest"}" ${loggedIn ? 'data-action="toggle-cloud"' : 'data-action="open-login"'}>
      <div class="cloud-toggle-icon">${icon("cloud", 16)}</div>
      <div class="cloud-toggle-copy">
        <div class="cloud-toggle-title">聊天记录保存云端</div>
        <div class="cloud-toggle-sub">${!loggedIn ? "登录后可开启跨设备同步" : cloudOn ? "开启后不可关闭，可跨设备继续" : "本会话仅保存在此设备，发送后不可再开启"}</div>
      </div>
      ${loggedIn ? `<span class="switch ${cloudOn ? "on" : ""}"></span>` : `<span class="text-btn">登录</span>`}
    </div>
  `;
  const connect = `
    <div class="connect-card">
      <div class="connect-head">
        <div class="connect-icon">${icon("settings", 16)}</div>
        <div>
          <div class="connect-title">先连接一个模型</div>
          <div class="connect-sub">支持 Responses 与 Chat Completions</div>
        </div>
      </div>
      <div class="connect-text">填入 Base URL、模型名称与 API Key，即可开始对话。密钥默认只留在本机；登录后可选择把部分服务商和云端会话同步到账号。</div>
      <button class="primary-btn block" data-action="open-settings">配置 AI 服务商</button>
      <div class="direct-note">${icon("lock", 10)} 本机存储 · 请求直连服务商</div>
    </div>
  `;
  const suggestions = `
    <div class="suggestion-label">试试这样开始</div>
    <div class="suggestion-grid">
      <button class="suggestion" data-action="suggestion" data-id="0"><span class="spark">${icon("plus", 13)}</span>解释一个复杂概念</button>
      <button class="suggestion" data-action="suggestion" data-id="1"><span class="spark">${icon("edit", 13)}</span>润色一段文字</button>
      <button class="suggestion" data-action="suggestion" data-id="2"><span class="spark">${icon("lock", 13)}</span>写一个产品方案</button>
      <button class="suggestion" data-action="suggestion" data-id="3"><span class="spark">${icon("arrow", 13)}</span>策划一次周末出走</button>
    </div>
  `;
  return `
    <div class="empty-wrap">
      <div class="hero-orb">R</div>
      <div class="hero-kicker">R E I N C H A T</div>
      <div class="hero-title">让思绪，发生。</div>
      <div class="hero-sub">一个安静、顺滑的 AI 对话空间</div>
      <div class="empty-column">
        ${cloud}
        ${provider ? suggestions : connect}
      </div>
    </div>
  `;
}

function updateSendState(): void {
  const send = document.getElementById("send-button") as HTMLButtonElement | null;
  if (!send) return;
  send.disabled = runtime.streaming !== null || runtime.importing || (!runtime.draft.trim() && runtime.attachments.length === 0);
}

function autoResizeDraft(): void {
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  if (!draft) return;
  draft.style.height = "auto";
  draft.style.height = `${Math.min(draft.scrollHeight, 172)}px`;
}

function renderAttachments(): void {
  const container = document.getElementById("attachments");
  if (!container) return;
  container.innerHTML = runtime.attachments.map((image) => `
    <div class="attachment">
      <img src="${image.dataUrl}" alt="" />
      <button data-action="remove-attachment" data-id="${image.id}" title="移除图片">×</button>
    </div>
  `).join("");
}

function renderComposer(): void {
  const provider = activeProvider();
  const chip = document.getElementById("provider-chip");
  if (chip) {
    chip.innerHTML = `<span class="model-dot"></span><span>${esc(provider ? `${provider.name} · ${activeModel(provider) || "未设置模型"}` : "配置服务商")}</span><span class="chevron">▾</span>`;
    chip.title = provider ? `${provider.name} · ${activeModel(provider)}` : "配置服务商";
  }
  const editBanner = document.getElementById("edit-banner");
  editBanner?.classList.toggle("visible", runtime.editing !== null);
  renderAttachments();
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  if (draft && draft.value !== runtime.draft) draft.value = runtime.draft;
  autoResizeDraft();
  const send = document.getElementById("send-button");
  const stop = document.getElementById("stop-button");
  send?.classList.toggle("hidden", runtime.streaming !== null);
  stop?.classList.toggle("hidden", runtime.streaming === null);
  updateSendState();
}

function renderAll(): void {
  renderConversations();
  renderAccountCard();
  renderHeader();
  renderChat();
  renderComposer();
}

function focusDraft(): void {
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  draft?.focus();
}

function toast(
  message: string,
  kind: "info" | "error" = "info",
  actionLabel?: string,
  onAction?: () => void,
  duration = 2600,
): void {
  const node = document.createElement("div");
  node.className = `toast ${kind === "error" ? "error" : ""}`;
  const copy = document.createElement("span");
  copy.textContent = message;
  node.appendChild(copy);
  if (actionLabel && onAction) {
    const button = document.createElement("button");
    button.className = "text-btn";
    button.textContent = actionLabel;
    button.addEventListener("click", () => {
      onAction();
      node.remove();
    });
    node.appendChild(button);
  }
  toastRoot.appendChild(node);
  window.setTimeout(() => {
    node.style.opacity = "0";
    node.style.transform = "translateY(5px)";
    node.style.transition = "opacity 180ms ease, transform 180ms ease";
    window.setTimeout(() => node.remove(), 200);
  }, duration);
}

function startNewChat(): void {
  if (runtime.streaming) {
    toast("请先停止当前生成");
    return;
  }
  state.activeConversationId = null;
  runtime.draft = "";
  runtime.attachments = [];
  runtime.editing = null;
  runtime.actionsOpen = null;
  state.newChatCloud = isLoggedIn();
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  if (draft) draft.value = "";
  scheduleSave();
  renderAll();
  focusDraft();
}

function selectConversation(id: string): void {
  if (!state.conversations.some((conversation) => conversation.id === id)) return;
  state.activeConversationId = id;
  runtime.editing = null;
  runtime.actionsOpen = null;
  scheduleSave();
  renderAll();
  const conversation = state.conversations.find((item) => item.id === id);
  if (conversation?.cloud && isLoggedIn()) void refreshCloudConversation(id);
  if (window.innerWidth <= 820) {
    runtime.sidebarOpen = false;
    document.body.classList.add("sidebar-collapsed");
  }
}

async function refreshCloudConversation(id: string): Promise<void> {
  if (!isLoggedIn()) return;
  const conversation = state.conversations.find((item) => item.id === id);
  if (!conversation) return;
  try {
    const account = state.cloudAccount;
    if (!account) return;
    const api = new CloudClient(account.serverUrl, account.token);
    const remote = await api.getConversation(id);
    if (remote.version <= conversation.cloudVersion && remote.updatedAt <= conversation.updatedAt) return;
    const converted = await remoteToLocalConversation(remote);
    updateConversation(converted);
    await saveNow();
    renderAll();
  } catch (error) {
    if (error instanceof CloudAuthError) handleAuthExpired();
  }
}

async function sendMessage(): Promise<void> {
  if (runtime.streaming) {
    toast("正在生成回复，请先停止");
    return;
  }
  if (runtime.importing) return;
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  const text = (draft?.value ?? runtime.draft).trim();
  runtime.draft = text;
  const attachments = runtime.attachments;
  if (!text && !attachments.length) return;
  const provider = activeProvider();
  if (!provider) {
    openSettings();
    toast("先配置一个 AI 服务商");
    return;
  }
  const baseError = baseUrlProblem(provider.baseUrl);
  if (baseError || !activeModel(provider)) {
    openProviderEditor(provider);
    toast(baseError || "请填写模型名称");
    return;
  }
  const key = normalizeApiKey(provider.apiKey);
  const keyProblem = key ? apiKeyProblem(key) : null;
  if (keyProblem) {
    openProviderEditor(provider);
    toast(keyProblem, "error");
    return;
  }

  const now = Date.now();
  let conversation = activeConversation();
  let baseTitle = conversation?.title;

  if (runtime.editing) {
    const editing = runtime.editing;
    const target = state.conversations.find((item) => item.id === editing.conversationId);
    if (target) {
      const index = target.messages.findIndex((message) => message.id === editing.messageId);
      if (index >= 0) {
        target.messages = target.messages.slice(0, index);
        conversation = target;
      }
    }
    runtime.editing = null;
  }

  if (!conversation) {
    conversation = {
      id: uid(),
      title: titleFromPrompt(text, attachments.length),
      createdAt: now,
      updatedAt: now,
      messages: [],
      cloud: isLoggedIn() && state.newChatCloud,
      cloudVersion: 0,
    };
    baseTitle = conversation.title;
  }

  const userMessage: ChatMessage = {
    id: uid(),
    role: "USER",
    content: text,
    images: attachments.map((image) => ({ ...image })),
    createdAt: now,
    status: "COMPLETE",
    error: null,
  };
  const assistantMessage: ChatMessage = {
    id: uid(),
    role: "ASSISTANT",
    content: "",
    images: [],
    createdAt: now + 1,
    status: "STREAMING",
    error: null,
  };

  conversation.messages.push(userMessage, assistantMessage);
  conversation.updatedAt = now;
  if (conversation.messages.filter((message) => message.role === "USER").length === 1) {
    conversation.title = titleFromPrompt(text, attachments.length);
  } else if (!baseTitle?.trim()) {
    conversation.title = titleFromPrompt(text, attachments.length);
  }
  updateConversation(conversation);
  state.activeConversationId = conversation.id;
  state.newChatCloud = false;
  runtime.draft = "";
  runtime.attachments = [];
  runtime.actionsOpen = null;
  if (draft) draft.value = "";
  runtime.importing = false;
  autoResizeDraft();
  runtime.streaming = {
    requestId: uid(),
    conversationId: conversation.id,
    messageId: assistantMessage.id,
  };
  scheduleSave();
  renderAll();
  scrollToBottom();

  const history = conversation.messages.slice(0, -1);
  try {
    await invoke("start_chat", {
      request: {
        requestId: runtime.streaming.requestId,
        endpoint: endpointUrl(provider.baseUrl, provider.protocol),
        protocol: provider.protocol,
        apiKey: key,
        body: buildPayload(provider, history),
      },
    });
  } catch (error) {
    finishStream("ERROR", error instanceof Error ? error.message : String(error));
  }
  focusDraft();
}

async function addImageFiles(files: File[]): Promise<void> {
  if (!files.length || runtime.importing) return;
  const room = MAX_IMAGES - runtime.attachments.length;
  if (room <= 0) {
    toast(`每轮对话最多添加 ${MAX_IMAGES} 张图片`);
    return;
  }
  runtime.importing = true;
  updateSendState();
  for (const file of files.slice(0, room)) {
    try {
      const imported = await importImageFile(file);
      runtime.attachments.push(imported.image);
    } catch (error) {
      toast(error instanceof Error ? error.message : "无法读取图片", "error");
    }
  }
  runtime.importing = false;
  renderComposer();
}

function finishStream(status: MessageStatus, error: string | null = null): void {
  const stream = runtime.streaming;
  if (!stream) return;
  const conversation = state.conversations.find((item) => item.id === stream.conversationId);
  const message = conversation?.messages.find((item) => item.id === stream.messageId);
  if (conversation && message) {
    if (status === "STOPPED" && !message.content.trim()) {
      conversation.messages = conversation.messages.filter((item) => item.id !== message.id);
    } else {
      message.status = status;
      message.error = error;
    }
    conversation.updatedAt = Date.now();
    updateConversation(conversation);
    runtime.streaming = null;
    scheduleSave();
    renderAll();
    if (conversation.cloud) {
      void syncConversation(conversation.id)
        .then(() => {
          runtime.lastSync = Date.now();
          renderAccountCard();
        })
        .catch((syncError) => handleCloudFailure(syncError));
    }
  } else {
    runtime.streaming = null;
    renderAll();
  }
}

function stopStreaming(): void {
  const stream = runtime.streaming;
  if (!stream) return;
  void invoke("stop_chat", { requestId: stream.requestId }).catch(() => undefined);
  finishStream("STOPPED", null);
}

function applyDelta(requestId: string, text: string): void {
  const stream = runtime.streaming;
  if (!stream || stream.requestId !== requestId) return;
  const conversation = state.conversations.find((item) => item.id === stream.conversationId);
  const message = conversation?.messages.find((item) => item.id === stream.messageId);
  if (!conversation || !message) return;
  message.content += text;
  message.status = "STREAMING";
  conversation.updatedAt = Date.now();
  updateConversation(conversation);
  scheduleSave();
  if (state.activeConversationId === conversation.id) renderChatSoon();
}

function toggleMessageActions(id: string): void {
  runtime.actionsOpen = runtime.actionsOpen === id ? null : id;
  renderChat();
}

function findMessage(id: string): { conversation: Conversation; message: ChatMessage } | null {
  for (const conversation of state.conversations) {
    const message = conversation.messages.find((item) => item.id === id);
    if (message) return { conversation, message };
  }
  return null;
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text);
    toast("已复制");
  } catch {
    const helper = document.createElement("textarea");
    helper.value = text;
    document.body.appendChild(helper);
    helper.select();
    document.execCommand("copy");
    helper.remove();
    toast("已复制");
  }
}

function beginEdit(id: string): void {
  if (runtime.streaming) {
    toast("请先停止当前生成");
    return;
  }
  const found = findMessage(id);
  if (!found || found.message.role !== "USER") return;
  state.activeConversationId = found.conversation.id;
  runtime.editing = { conversationId: found.conversation.id, messageId: id };
  runtime.draft = found.message.content;
  runtime.attachments = found.message.images.map((image) => ({ ...image }));
  runtime.actionsOpen = null;
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  if (draft) draft.value = runtime.draft;
  scheduleSave();
  renderAll();
  focusDraft();
}

function rewindTo(id: string): void {
  if (runtime.streaming) {
    toast("请先停止当前生成");
    return;
  }
  const conversation = activeConversation();
  if (!conversation) return;
  const index = conversation.messages.findIndex((message) => message.id === id);
  if (index < 0 || index >= conversation.messages.length - 1) return;
  runtime.rewindUndo = {
    conversation: deepClone(conversation),
    activeConversationId: state.activeConversationId,
  };
  const updated = deepClone(conversation);
  updated.messages = updated.messages.slice(0, index + 1);
  updated.updatedAt = Date.now();
  updateConversation(updated);
  runtime.actionsOpen = null;
  scheduleSave();
  renderAll();
  toast("已回退到此处", "info", "撤销", () => undoRewind(), 6000);
  window.setTimeout(() => {
    if (runtime.rewindUndo?.conversation.id === conversation.id) runtime.rewindUndo = null;
  }, 5000);
  if (updated.cloud) void syncConversation(updated.id).catch(handleCloudFailure);
}

function undoRewind(): void {
  const undo = runtime.rewindUndo;
  if (!undo) return;
  runtime.rewindUndo = null;
  state.conversations = state.conversations
    .filter((conversation) => conversation.id !== undo.conversation.id)
    .concat([undo.conversation])
    .sort((a, b) => b.updatedAt - a.updatedAt);
  state.activeConversationId = undo.activeConversationId ?? undo.conversation.id;
  scheduleSave();
  renderAll();
  toast("已撤销回退");
}

function retryMessage(id: string): void {
  if (runtime.streaming) {
    toast("请先停止当前生成");
    return;
  }
  const found = findMessage(id);
  if (!found || found.message.role !== "ASSISTANT") return;
  const provider = activeProvider();
  if (!provider) {
    openSettings();
    toast("先配置一个 AI 服务商");
    return;
  }
  const conversation = found.conversation;
  const index = conversation.messages.findIndex((message) => message.id === id);
  const userIndex = conversation.messages.slice(0, index).findLastIndex((message) => message.role === "USER");
  if (userIndex < 0) {
    toast("找不到对应的提问");
    return;
  }
  const history = conversation.messages.slice(0, userIndex + 1);
  const assistant: ChatMessage = {
    id: uid(),
    role: "ASSISTANT",
    content: "",
    images: [],
    createdAt: Date.now() + 1,
    status: "STREAMING",
    error: null,
  };
  conversation.messages = [...history, assistant];
  conversation.updatedAt = Date.now();
  updateConversation(conversation);
  state.activeConversationId = conversation.id;
  runtime.actionsOpen = null;
  runtime.streaming = { requestId: uid(), conversationId: conversation.id, messageId: assistant.id };
  scheduleSave();
  renderAll();
  const key = normalizeApiKey(provider.apiKey);
  void invoke("start_chat", {
    request: {
      requestId: runtime.streaming.requestId,
      endpoint: endpointUrl(provider.baseUrl, provider.protocol),
      protocol: provider.protocol,
      apiKey: key,
      body: buildPayload(provider, history),
    },
  }).catch((error) => finishStream("ERROR", error instanceof Error ? error.message : String(error)));
}

function newConversation(): void {
  startNewChat();
}

function openSettings(): void {
  runtime.overlay = "settings";
  renderOverlay();
}

function openLogin(mode: "login" | "register" = "login"): void {
  runtime.overlay = "login";
  runtime.loginMode = mode;
  runtime.loginError = "";
  runtime.loginBusy = false;
  runtime.loginServer = state.cloudServerUrl || DEFAULT_CLOUD_SERVER;
  runtime.loginUsername = "";
  runtime.loginPassword = "";
  renderOverlay();
  window.setTimeout(() => (document.getElementById("login-username") as HTMLInputElement | null)?.focus(), 60);
}

function closeOverlay(): void {
  runtime.overlay = null;
  runtime.providerDraft = null;
  overlayRoot.innerHTML = "";
}

function openProviderEditor(provider: Provider | null): void {
  runtime.providerDraft = provider ? deepClone(provider) : {
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
  runtime.providerIsNew = !provider;
  runtime.showProviderKey = false;
  runtime.overlay = "provider";
  renderOverlay();
  window.setTimeout(() => (document.getElementById("provider-name") as HTMLInputElement | null)?.focus(), 60);
}

function openProviderPicker(): void {
  if (!state.providers.length) {
    openSettings();
    return;
  }
  runtime.modelPickerProviderId = activeProvider()?.id ?? state.providers[0]?.id ?? null;
  runtime.overlay = "provider-picker";
  renderOverlay();
}

function setActiveProvider(id: string): void {
  if (!state.providers.some((provider) => provider.id === id)) return;
  state.activeProviderId = id;
  scheduleSave();
  renderAll();
}

function setActiveModel(providerId: string, model: string): void {
  const provider = state.providers.find((item) => item.id === providerId);
  if (!provider || !modelOptions(provider.model).includes(model)) return;
  provider.selectedModel = model;
  state.activeProviderId = providerId;
  scheduleSave();
  renderAll();
}

async function saveProviderDraft(): Promise<void> {
  const draft = runtime.providerDraft;
  if (!draft) return;
  const name = draft.name.trim() || "未命名服务商";
  const baseUrl = draft.baseUrl.trim();
  const options = modelOptions(draft.model);
  const baseError = baseUrlProblem(baseUrl);
  const error = document.getElementById("provider-error");
  if (baseError) {
    if (error) error.textContent = baseError;
    return;
  }
  if (!options.length) {
    if (error) error.textContent = "请至少填写一个模型名称";
    return;
  }
  const key = normalizeApiKey(draft.apiKey);
  if (key) {
    const problem = apiKeyProblem(key);
    if (problem) {
      if (error) error.textContent = problem;
      return;
    }
  }
  const previous = state.providers.find((provider) => provider.id === draft.id);
  const selected = options.includes(draft.selectedModel) ? draft.selectedModel : options[0];
  const provider: Provider = {
    ...draft,
    name,
    baseUrl,
    apiKey: key,
    model: options.join(", "),
    selectedModel: selected,
    temperature: Number.isFinite(draft.temperature as number) ? draft.temperature : null,
    maxTokens: Number.isFinite(draft.maxTokens as number) ? draft.maxTokens : null,
    localOnly: !isLoggedIn() ? true : draft.localOnly,
    cloudSynced: false,
  };
  const index = state.providers.findIndex((item) => item.id === provider.id);
  if (index >= 0) state.providers[index] = provider;
  else state.providers.unshift(provider);
  state.activeProviderId = provider.id;
  if (provider.localOnly && previous?.localOnly === false) {
    if (!state.pendingCloudProviderDeletes.includes(provider.id)) state.pendingCloudProviderDeletes.push(provider.id);
  } else {
    state.pendingCloudProviderDeletes = state.pendingCloudProviderDeletes.filter((id) => id !== provider.id);
  }
  scheduleSave();
  closeOverlay();
  renderAll();
  if (isLoggedIn()) {
    runtime.cloudBusy = true;
    renderAccountCard();
    try {
      await syncProvider(provider.id);
      if (provider.localOnly) {
        state.pendingCloudProviderDeletes = state.pendingCloudProviderDeletes.filter((id) => id !== provider.id);
        await saveNow();
      }
      toast(provider.localOnly ? "服务商已保存在本机" : "服务商已同步到账号");
    } catch (syncError) {
      handleCloudFailure(syncError, false);
    } finally {
      runtime.cloudBusy = false;
      renderAccountCard();
    }
  } else {
    toast("服务商已保存在本机");
  }
}

function deleteProvider(id: string): void {
  const provider = state.providers.find((item) => item.id === id);
  if (!provider) return;
  if (!window.confirm(`删除服务商「${provider.name}」？`)) return;
  state.providers = state.providers.filter((item) => item.id !== id);
  if (state.activeProviderId === id) state.activeProviderId = state.providers[0]?.id ?? null;
  if (!provider.localOnly && isLoggedIn()) {
    if (!state.pendingCloudProviderDeletes.includes(id)) state.pendingCloudProviderDeletes.push(id);
  }
  scheduleSave();
  renderAll();
  renderOverlay();
  void deleteRemoteProvider(id)
    .then(async () => {
      state.pendingCloudProviderDeletes = state.pendingCloudProviderDeletes.filter((item) => item !== id);
      await saveNow();
    })
    .catch((error) => handleCloudFailure(error, false));
  toast("服务商已删除");
}

function handleCloudFailure(error: unknown, showToast = true): void {
  if (error instanceof CloudAuthError) {
    handleAuthExpired();
    return;
  }
  const message = error instanceof CloudError
    ? error.message
    : error instanceof Error
      ? error.message
      : String(error);
  runtime.syncError = message;
  if (showToast) toast(message, "error");
  renderAccountCard();
}

function handleAuthExpired(): void {
  if (!state.cloudAccount) return;
  state.cloudAccount = null;
  state.conversations = state.conversations.filter((conversation) => !conversation.cloud);
  state.providers = state.providers.filter((provider) => provider.localOnly);
  state.pendingCloudConversationDeletes = [];
  state.pendingCloudProviderDeletes = [];
  state.newChatCloud = false;
  state.activeConversationId = state.conversations.some((conversation) => conversation.id === state.activeConversationId)
    ? state.activeConversationId
    : (state.conversations[0]?.id ?? null);
  if (!state.providers.some((provider) => provider.id === state.activeProviderId)) {
    state.activeProviderId = state.providers[0]?.id ?? null;
  }
  runtime.syncError = "";
  runtime.cloudBusy = false;
  void saveNow();
  renderAll();
  toast("登录已过期，请重新登录", "error");
}

async function handleLogout(): Promise<void> {
  if (!state.cloudAccount) return;
  if (!window.confirm("退出登录？本机缓存的云端会话和同步服务商会移除，云端数据保留。")) return;
  const account = state.cloudAccount;
  state.cloudAccount = null;
  state.conversations = state.conversations.filter((conversation) => !conversation.cloud);
  state.providers = state.providers.filter((provider) => provider.localOnly);
  state.pendingCloudConversationDeletes = [];
  state.pendingCloudProviderDeletes = [];
  state.newChatCloud = false;
  state.activeConversationId = state.conversations.some((conversation) => conversation.id === state.activeConversationId)
    ? state.activeConversationId
    : (state.conversations[0]?.id ?? null);
  if (!state.providers.some((provider) => provider.id === state.activeProviderId)) {
    state.activeProviderId = state.providers[0]?.id ?? null;
  }
  runtime.syncError = "";
  await saveNow();
  closeOverlay();
  renderAll();
  try {
    await new CloudClient(account.serverUrl, account.token).logout();
  } catch {
    // Local logout is enough if the token already expired.
  }
  toast("已退出登录");
}

async function syncCloud(): Promise<void> {
  if (!isLoggedIn()) {
    openLogin();
    return;
  }
  runtime.cloudBusy = true;
  runtime.syncError = "";
  renderAccountCard();
  try {
    await syncAll();
    runtime.lastSync = Date.now();
    toast("云端数据已同步");
  } catch (error) {
    handleCloudFailure(error);
  } finally {
    runtime.cloudBusy = false;
    renderAccountCard();
    renderAll();
  }
}

async function submitLogin(): Promise<void> {
  if (runtime.loginBusy) return;
  const server = runtime.loginServer.trim() || DEFAULT_CLOUD_SERVER;
  const username = runtime.loginUsername.trim();
  const password = runtime.loginPassword;
  runtime.loginError = "";
  runtime.loginBusy = true;
  updateLoginStatus();
  try {
    const base = CloudClient.normalizeServer(server);
    const api = new CloudClient(base, "");
    const auth = runtime.loginMode === "register"
      ? await api.register(username, password)
      : await api.login(username, password);
    state.cloudAccount = {
      username: auth.user.username,
      serverUrl: base,
      token: auth.token,
      expiresAt: auth.expiresAt,
    };
    state.cloudServerUrl = base;
    state.newChatCloud = true;
    await saveNow();
    closeOverlay();
    renderAll();
    toast(runtime.loginMode === "register" ? "注册成功，已登录" : "已登录");
    void syncCloud();
  } catch (error) {
    runtime.loginError = error instanceof Error ? error.message : String(error);
    runtime.loginBusy = false;
    updateLoginStatus();
  }
}

function updateLoginStatus(): void {
  const error = document.getElementById("login-error");
  if (error) error.textContent = runtime.loginError;
  const submit = document.getElementById("login-submit") as HTMLButtonElement | null;
  if (submit) {
    submit.disabled = runtime.loginBusy;
    submit.textContent = runtime.loginBusy
      ? (runtime.loginMode === "register" ? "正在创建账号…" : "正在登录…")
      : (runtime.loginMode === "register" ? "创建并登录" : "登录");
  }
}

function loginOverlay(): string {
  return `
    <div class="overlay-backdrop" data-overlay-backdrop="1">
      <div class="modal small">
        <div class="modal-head">
          <div class="brand-orb" style="width:34px;height:34px;font-size:16px">R</div>
          <div class="modal-head-copy">
            <div class="modal-title">${runtime.loginMode === "register" ? "创建账号" : "登录 ReinChat"}</div>
            <div class="modal-sub">多端同步你的对话与服务商</div>
          </div>
          <button class="close-btn" data-action="close-overlay">${icon("close", 14)}</button>
        </div>
        <div class="modal-body">
          <div class="segmented">
            <button class="${runtime.loginMode === "login" ? "active" : ""}" data-action="login-mode" data-mode="login">登录</button>
            <button class="${runtime.loginMode === "register" ? "active" : ""}" data-action="login-mode" data-mode="register">注册</button>
          </div>
          <div class="form-field">
            <div class="form-label">服务器地址</div>
            <input class="input" id="login-server" value="${esc(runtime.loginServer)}" placeholder="https://reinchat.markjy.com" />
            <div class="form-hint">默认 https://reinchat.markjy.com；也可填自建地址</div>
          </div>
          <div class="form-field">
            <div class="form-label">账号</div>
            <input class="input" id="login-username" value="${esc(runtime.loginUsername)}" placeholder="3–32 位用户名" autocomplete="username" />
          </div>
          <div class="form-field">
            <div class="form-label">密码</div>
            <input class="input" id="login-password" type="password" value="${esc(runtime.loginPassword)}" placeholder="${runtime.loginMode === "register" ? "至少 8 位" : "输入密码"}" autocomplete="current-password" />
          </div>
          <div class="form-error" id="login-error">${esc(runtime.loginError)}</div>
          <button class="primary-btn block" id="login-submit" data-action="login-submit" ${runtime.loginBusy ? "disabled" : ""}>${runtime.loginBusy ? (runtime.loginMode === "register" ? "正在创建账号…" : "正在登录…") : (runtime.loginMode === "register" ? "创建并登录" : "登录")}</button>
          <div class="form-hint" style="margin-top:12px;line-height:1.7">登录凭据只发送到你填写的服务器。未标记“仅本地”的服务商配置会同步到该账号；云端会话会保存在服务器并同步到同账号设备。</div>
        </div>
      </div>
    </div>
  `;
}

function settingsOverlay(): string {
  const account = state.cloudAccount;
  const accountHtml = !account
    ? `<div class="account-panel">
        <div class="account-panel-status" style="margin-top:0">登录后可在多台设备继续同一段对话，并同步未标记“仅本地”的服务商。</div>
        <button class="primary-btn" style="margin-top:13px" data-action="open-login">登录 / 注册</button>
      </div>`
    : `<div class="account-panel">
        <div class="account-panel-row">
          <div class="account-avatar">${esc(senderInitial(account.username))}</div>
          <div style="min-width:0;flex:1">
            <div class="account-panel-name">${esc(account.username)}</div>
            <div class="account-panel-host">${esc(hostOf(account.serverUrl))}</div>
          </div>
        </div>
        <div class="account-panel-status ${runtime.syncError ? "error" : ""}">${esc(runtime.cloudBusy || isSyncing() ? "正在同步云端数据…" : runtime.syncError || "多端同步已开启，新会话默认保存云端")}</div>
        <div class="account-panel-actions">
          <button class="ghost-btn" data-action="sync-cloud">${runtime.cloudBusy || isSyncing() ? "同步中…" : "立即同步"}</button>
          <button class="ghost-btn danger" data-action="logout">退出登录</button>
        </div>
      </div>`;
  const providers = state.providers.length
    ? state.providers.map((provider) => `
        <div class="provider-row ${provider.id === state.activeProviderId ? "active" : ""}" data-action="edit-provider" data-id="${provider.id}">
          <div class="provider-logo">${esc(senderInitial(provider.name))}</div>
          <div class="provider-copy">
            <div class="provider-name">${esc(provider.name)} <span class="mini-badge ${provider.localOnly ? "" : "cloud"}">${provider.localOnly ? "仅本地" : "云端"}</span>${provider.id === state.activeProviderId ? '<span class="mini-badge">当前</span>' : ""}</div>
            <div class="provider-detail">${esc(activeModel(provider) || "未填写模型")} · ${esc(provider.protocol === "RESPONSES" ? "Responses" : "Chat Completions")}</div>
          </div>
          <div class="row-actions">
            <button class="icon-button" data-action="delete-provider" data-id="${provider.id}" title="删除">${icon("trash", 13)}</button>
          </div>
        </div>
      `).join("")
    : `<div style="color:var(--text-3);font-size:11px;padding:8px 2px 12px">还没有服务商。</div>`;
  return `
    <div class="overlay-backdrop" data-overlay-backdrop="1">
      <div class="modal">
        <div class="modal-head">
          <div class="modal-head-copy">
            <div class="modal-title">设置</div>
            <div class="modal-sub">账户 · 服务商 · 本机数据</div>
          </div>
          <button class="close-btn" data-action="close-overlay">${icon("close", 14)}</button>
        </div>
        <div class="modal-body">
          <div class="section-title">账户</div>
          ${accountHtml}
          <div class="section-title">AI 服务商 <span class="section-count">${state.providers.length}</span></div>
          <div class="provider-list">${providers}</div>
          <button class="add-provider" data-action="new-provider">${icon("plus", 13)} 添加服务商</button>
          <div class="privacy-card"><strong>密钥与数据的去向</strong><br />API Key 默认加密保存在本机。关闭服务商的“仅保存在本机”后，完整配置（含 API Key）会加密同步到你的账号服务器；标记为云端的会话也会同步到同账号设备。</div>
        </div>
        <div class="modal-footer">
          <span style="font-size:9.5px;color:var(--text-3)">ReinChat 0.3.0 · Windows</span>
          <span class="spacer"></span>
          <button class="ghost-btn" data-action="close-overlay">关闭</button>
        </div>
      </div>
    </div>
  `;
}

function providerOverlay(): string {
  const draft = runtime.providerDraft;
  if (!draft) return "";
  const options = modelOptions(draft.model);
  const localOnly = !isLoggedIn() ? true : draft.localOnly;
  return `
    <div class="overlay-backdrop" data-overlay-backdrop="1">
      <div class="modal">
        <div class="modal-head">
          <div class="modal-head-copy">
            <div class="modal-title">${runtime.providerIsNew ? "添加服务商" : "编辑服务商"}</div>
            <div class="modal-sub">Responses 或 Chat Completions</div>
          </div>
          <button class="close-btn" data-action="close-overlay">${icon("close", 14)}</button>
        </div>
        <div class="modal-body">
          <div class="form-field">
            <div class="form-label">显示名称</div>
            <input class="input" id="provider-name" data-field="name" value="${esc(draft.name)}" placeholder="例如：DeepSeek" />
          </div>
          <div class="row-2">
            <div class="form-field">
              <div class="form-label">协议</div>
              <select class="select" data-field="protocol" id="provider-protocol">
                <option value="CHAT_COMPLETIONS" ${draft.protocol === "CHAT_COMPLETIONS" ? "selected" : ""}>Chat Completions</option>
                <option value="RESPONSES" ${draft.protocol === "RESPONSES" ? "selected" : ""}>Responses</option>
              </select>
            </div>
            <div class="form-field">
              <div class="form-label">快捷模板</div>
              <select class="select" id="provider-template">
                <option value="">选择模板…</option>
                <option value="openai">OpenAI</option>
                <option value="deepseek">DeepSeek</option>
                <option value="openrouter">OpenRouter</option>
              </select>
            </div>
          </div>
          <div class="form-field">
            <div class="form-label">Base URL</div>
            <input class="input" data-field="baseUrl" value="${esc(draft.baseUrl)}" placeholder="https://api.openai.com/v1" />
            <div class="form-hint">请求地址：${esc(draft.baseUrl ? endpointUrl(draft.baseUrl, draft.protocol) : "填写 Base URL 后生成")}</div>
          </div>
          <div class="form-field">
            <div class="form-label">模型（可填多个）</div>
            <input class="input" data-field="model" value="${esc(draft.model)}" placeholder="gpt-4o-mini, gpt-4.1-mini" />
            <div class="form-hint">多个模型用英文逗号分隔；对话页可切换。${options.length > 1 ? `当前已识别 ${options.length} 个模型。` : ""}</div>
            ${options.length > 1 ? `<div class="model-options" style="margin-top:8px">${options.map((model) => `<span class="model-chip ${model === draft.selectedModel ? "active" : ""}">${esc(model)}</span>`).join("")}</div>` : ""}
          </div>
          <div class="form-field">
            <div class="form-label">API Key <button class="text-btn" type="button" data-action="toggle-provider-key">${runtime.showProviderKey ? "隐藏" : "显示"}</button></div>
            <input class="input" data-field="apiKey" type="${runtime.showProviderKey ? "text" : "password"}" value="${esc(draft.apiKey)}" placeholder="sk-…（可留空）" />
            <div class="form-hint">加密保存在本机；隐藏字符会自动清理；留空时不发送 Authorization 头。</div>
          </div>
          <div class="form-field">
            <div class="form-label">系统提示词（可选）</div>
            <textarea class="textarea" data-field="systemPrompt" placeholder="为这位助手设定身份、语气或回答边界">${esc(draft.systemPrompt)}</textarea>
          </div>
          <div class="row-2">
            <div class="form-field">
              <div class="form-label">temperature（可选）</div>
              <input class="input" data-field="temperature" data-number="1" value="${draft.temperature ?? ""}" placeholder="例如 0.7" />
            </div>
            <div class="form-field">
              <div class="form-label">最大输出长度（可选）</div>
              <input class="input" data-field="maxTokens" data-number="1" value="${draft.maxTokens ?? ""}" placeholder="例如 4096" />
            </div>
          </div>
          <div class="switch-row ${isLoggedIn() ? "" : "disabled"}" data-action="toggle-local-only">
            <div class="cloud-toggle-icon">${icon("cloud", 15)}</div>
            <div class="switch-row-copy">
              <div class="switch-row-title">仅保存在本机</div>
              <div class="switch-row-sub">${!isLoggedIn() ? "未登录时固定开启；登录后可关闭并同步" : localOnly ? "此服务商的配置和 API Key 不会上传" : "配置和 API Key 会加密同步到你的账号"}</div>
            </div>
            <span class="switch ${localOnly ? "on" : ""}"></span>
          </div>
          <div class="form-error" id="provider-error"></div>
        </div>
        <div class="modal-footer">
          <span style="font-size:9px;color:var(--text-3)">配置默认只保存在本机</span>
          <span class="spacer"></span>
          <button class="ghost-btn" data-action="close-overlay">取消</button>
          <button class="primary-btn" data-action="save-provider">保存</button>
        </div>
      </div>
    </div>
  `;
}

function providerPickerOverlay(): string {
  const active = activeProvider();
  const provider = state.providers.find((item) => item.id === runtime.modelPickerProviderId) ?? active;
  if (!provider) return "";
  const models = modelOptions(provider.model);
  return `
    <div class="overlay-backdrop" data-overlay-backdrop="1">
      <div class="modal medium">
        <div class="modal-head">
          <div class="modal-head-copy">
            <div class="modal-title">选择服务商与模型</div>
            <div class="modal-sub">切换后，下一条消息将使用新的模型</div>
          </div>
          <button class="close-btn" data-action="close-overlay">${icon("close", 14)}</button>
        </div>
        <div class="modal-body">
          <div class="section-title">当前服务商的模型 <span class="section-count">${models.length}</span></div>
          <div class="model-options">
            ${models.length
              ? models.map((model) => `<button class="model-chip ${model === activeModel(provider) ? "active" : ""}" data-action="select-model" data-provider="${provider.id}" data-model="${esc(model)}">${esc(model)}</button>`).join("")
              : `<span style="font-size:10px;color:var(--text-3)">该服务商还没有模型</span>`}
          </div>
          <div class="section-title">服务商</div>
          <div class="model-options" style="flex-direction:column;align-items:stretch">
            ${state.providers.map((item) => `
              <div class="provider-picker-row ${item.id === provider.id ? "active" : ""}" data-action="select-provider" data-id="${item.id}">
                <span>${esc(item.name)}</span>
                <span style="font-size:9px;color:var(--text-3)">${esc(activeModel(item) || "未设置模型")}</span>
                ${item.id === provider.id ? `<span class="check">${icon("check", 13)}</span>` : ""}
              </div>
            `).join("")}
          </div>
          <div style="margin-top:16px"><button class="ghost-btn" data-action="open-settings">管理服务商</button></div>
        </div>
      </div>
    </div>
  `;
}

function renderOverlay(): void {
  if (!runtime.overlay) {
    overlayRoot.innerHTML = "";
    return;
  }
  if (runtime.overlay === "login") overlayRoot.innerHTML = loginOverlay();
  else if (runtime.overlay === "settings") overlayRoot.innerHTML = settingsOverlay();
  else if (runtime.overlay === "provider") overlayRoot.innerHTML = providerOverlay();
  else if (runtime.overlay === "provider-picker") overlayRoot.innerHTML = providerPickerOverlay();
}

function renderImageViewer(): void {
  if (!runtime.viewerImage) {
    imageRoot.innerHTML = "";
    return;
  }
  imageRoot.innerHTML = `<div class="image-viewer" data-action="close-image"><button class="close" data-action="close-image">${icon("close", 16)}</button><img src="${runtime.viewerImage.dataUrl}" alt="图片预览" /></div>`;
}

function findProvider(id: string): Provider | null {
  return state.providers.find((provider) => provider.id === id) ?? null;
}

function syncProviderDraftField(target: EventTarget | null): void {
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement)) return;
  const field = target.dataset.field;
  if (!field || !runtime.providerDraft) return;
  const draft = runtime.providerDraft;
  if (field === "temperature" || field === "maxTokens") {
    const value = target.value.trim();
    const parsed = value === "" ? null : Number(value);
    if (field === "temperature") draft.temperature = parsed !== null && Number.isFinite(parsed) ? parsed : null;
    else draft.maxTokens = parsed !== null && Number.isFinite(parsed) ? Math.max(1, Math.round(parsed)) : null;
    return;
  }
  if (field === "protocol") {
    draft.protocol = target.value === "RESPONSES" ? "RESPONSES" : "CHAT_COMPLETIONS";
    return;
  }
  (draft as unknown as Record<string, unknown>)[field] = target.value;
}

function applyProviderTemplate(name: string): void {
  if (!runtime.providerDraft || !name) return;
  const template = name === "openai"
    ? { baseUrl: "https://api.openai.com/v1", model: "gpt-4o-mini, gpt-4.1-mini" }
    : name === "deepseek"
      ? { baseUrl: "https://api.deepseek.com/v1", model: "deepseek-chat, deepseek-reasoner" }
      : { baseUrl: "https://openrouter.ai/api/v1", model: "openai/gpt-4o-mini" };
  runtime.providerDraft.baseUrl = template.baseUrl;
  runtime.providerDraft.model = template.model;
  runtime.providerDraft.selectedModel = modelOptions(template.model)[0] ?? "";
  renderOverlay();
}

function handleLoginField(target: EventTarget | null): void {
  if (!(target instanceof HTMLInputElement)) return;
  if (target.id === "login-server") runtime.loginServer = target.value;
  else if (target.id === "login-username") runtime.loginUsername = target.value;
  else if (target.id === "login-password") runtime.loginPassword = target.value;
}

function handleAction(action: string, node: HTMLElement): void {
  const id = node.dataset.id ?? "";
  switch (action) {
    case "toggle-sidebar":
      runtime.sidebarOpen = !runtime.sidebarOpen;
      document.body.classList.toggle("sidebar-collapsed", !runtime.sidebarOpen);
      break;
    case "new-chat":
      newConversation();
      break;
    case "open-settings":
      openSettings();
      break;
    case "close-overlay":
      closeOverlay();
      break;
    case "open-login":
      openLogin();
      break;
    case "login-mode":
      runtime.loginMode = node.dataset.mode === "register" ? "register" : "login";
      runtime.loginError = "";
      renderOverlay();
      break;
    case "login-submit":
      void submitLogin();
      break;
    case "logout":
      void handleLogout();
      break;
    case "sync-cloud":
      void syncCloud();
      break;
    case "toggle-cloud":
      if (!isLoggedIn()) openLogin();
      else {
        state.newChatCloud = !state.newChatCloud;
        scheduleSave();
        renderChat();
      }
      break;
    case "suggestion":
      setSuggestion(Number(node.dataset.id ?? 0));
      break;
    case "open-provider-picker":
      openProviderPicker();
      break;
    case "select-provider":
      setActiveProvider(id);
      runtime.modelPickerProviderId = id;
      renderOverlay();
      renderAll();
      break;
    case "select-model":
      setActiveModel(node.dataset.provider ?? id, node.dataset.model ?? "");
      closeOverlay();
      break;
    case "select-conversation":
      selectConversation(id);
      break;
    case "rename-conversation":
      renameConversation(id);
      break;
    case "delete-conversation":
      void deleteConversation(id);
      break;
    case "toggle-message":
      toggleMessageActions(id);
      break;
    case "copy-message": {
      const found = findMessage(id);
      if (found) void copyText(found.message.content);
      break;
    }
    case "copy-code": {
      const block = node.closest(".code-block");
      const code = block?.querySelector("code")?.textContent ?? "";
      void copyText(code);
      break;
    }
    case "edit-message":
      beginEdit(id);
      break;
    case "rewind-message":
      rewindTo(id);
      break;
    case "retry-message":
      retryMessage(id);
      break;
    case "view-image": {
      const found = findMessage(id);
      const index = Number(node.dataset.imageIndex ?? 0);
      const image = found?.message.images[index];
      if (image) {
        runtime.viewerImage = image;
        renderImageViewer();
      }
      break;
    }
    case "close-image":
      runtime.viewerImage = null;
      renderImageViewer();
      break;
    case "attach-image":
      (document.getElementById("file-input") as HTMLInputElement | null)?.click();
      break;
    case "remove-attachment":
      runtime.attachments = runtime.attachments.filter((image) => image.id !== id);
      renderComposer();
      break;
    case "send":
      void sendMessage();
      break;
    case "stop":
      stopStreaming();
      break;
    case "cancel-edit":
      runtime.editing = null;
      runtime.draft = "";
      runtime.attachments = [];
      const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
      if (draft) draft.value = "";
      renderComposer();
      break;
    case "new-provider":
      openProviderEditor(null);
      break;
    case "edit-provider": {
      const provider = findProvider(id);
      if (provider) openProviderEditor(provider);
      break;
    }
    case "delete-provider":
      deleteProvider(id);
      break;
    case "save-provider":
      void saveProviderDraft();
      break;
    case "toggle-local-only":
      if (!runtime.providerDraft || !isLoggedIn()) break;
      runtime.providerDraft.localOnly = !runtime.providerDraft.localOnly;
      renderOverlay();
      break;
    case "toggle-provider-key":
      runtime.showProviderKey = !runtime.showProviderKey;
      renderOverlay();
      break;
    case "provider-template":
      applyProviderTemplate((node as HTMLSelectElement).value);
      break;
    default:
      break;
  }
}

function renameConversation(id: string): void {
  const conversation = state.conversations.find((item) => item.id === id);
  if (!conversation) return;
  const name = window.prompt("重命名会话", conversation.title);
  if (name === null) return;
  const trimmed = name.trim();
  if (!trimmed) return;
  conversation.title = trimmed.slice(0, 120);
  conversation.updatedAt = Date.now();
  updateConversation(conversation);
  scheduleSave();
  renderAll();
}

async function deleteConversation(id: string): Promise<void> {
  const conversation = state.conversations.find((item) => item.id === id);
  if (!conversation) return;
  if (!window.confirm(`删除会话「${conversation.title}」？`)) return;
  const cloud = conversation.cloud;
  removeConversation(id);
  if (cloud && isLoggedIn()) {
    if (!state.pendingCloudConversationDeletes.includes(id)) state.pendingCloudConversationDeletes.push(id);
    void deleteRemoteConversation(id)
      .then(async () => {
        state.pendingCloudConversationDeletes = state.pendingCloudConversationDeletes.filter((item) => item !== id);
        await saveNow();
      })
      .catch((error) => handleCloudFailure(error, false));
  }
  scheduleSave();
  renderAll();
  toast("会话已删除");
}

const SUGGESTIONS = ["解释一个复杂概念", "润色一段文字", "写一个产品方案", "策划一次周末出走"];

function setSuggestion(index: number): void {
  const text = SUGGESTIONS[index] ?? SUGGESTIONS[0];
  runtime.draft = text;
  const draft = document.getElementById("draft") as HTMLTextAreaElement | null;
  if (draft) {
    draft.value = text;
    draft.focus();
    autoResizeDraft();
  }
  updateSendState();
}

function handleClick(event: MouseEvent): void {
  const target = event.target as HTMLElement;
  const link = target.closest("a[data-external-link]") as HTMLAnchorElement | null;
  if (link) {
    event.preventDefault();
    void openUrl(link.href);
    return;
  }
  const actionNode = target.closest("[data-action]") as HTMLElement | null;
  if (actionNode) {
    const action = actionNode.dataset.action;
    if (action) {
      event.preventDefault();
      handleAction(action, actionNode);
      return;
    }
  }
  const toggle = target.closest("[data-message-toggle]") as HTMLElement | null;
  if (toggle) {
    toggleMessageActions(toggle.dataset.messageToggle ?? "");
    return;
  }
  if (target.dataset.overlayBackdrop === "1") closeOverlay();
}

function handleInput(event: Event): void {
  const target = event.target;
  if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement)) return;
  if (target.id === "search-input") return;
  if (target.id.startsWith("login-")) {
    handleLoginField(target);
    return;
  }
  if (target.dataset.field) {
    syncProviderDraftField(target);
    if (target.dataset.field === "baseUrl" || target.dataset.field === "protocol" || target.dataset.field === "model") {
      const hint = target.closest(".form-field")?.querySelector(".form-hint");
      if (hint && target.dataset.field === "baseUrl" && runtime.providerDraft) {
        try {
          hint.textContent = `请求地址：${endpointUrl(runtime.providerDraft.baseUrl, runtime.providerDraft.protocol)}`;
        } catch {
          hint.textContent = "填写 Base URL 后生成";
        }
      }
    }
  }
}

function handleChange(event: Event): void {
  const target = event.target as HTMLElement;
  if (target.id === "provider-protocol" || target.dataset.field) syncProviderDraftField(target);
  if (target.id === "provider-template") {
    applyProviderTemplate((target as HTMLSelectElement).value);
  }
}

function closeTopLayer(): boolean {
  if (runtime.viewerImage) {
    runtime.viewerImage = null;
    renderImageViewer();
    return true;
  }
  if (runtime.overlay) {
    closeOverlay();
    return true;
  }
  return false;
}

function attachListeners(): void {
  document.addEventListener("click", handleClick);
  document.addEventListener("input", handleInput);
  document.addEventListener("change", handleChange);
  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if (closeTopLayer()) event.preventDefault();
      return;
    }
    if (event.ctrlKey && event.key.toLowerCase() === "n") {
      event.preventDefault();
      startNewChat();
    }
    if (event.ctrlKey && event.key === ",") {
      event.preventDefault();
      openSettings();
    }
    if (event.key === "Enter" && !event.shiftKey && runtime.overlay === "login" && !runtime.loginBusy) {
      const active = document.activeElement;
      if (active && (active.id === "login-username" || active.id === "login-password" || active.id === "login-server")) {
        event.preventDefault();
        void submitLogin();
      }
    }
  });
  window.addEventListener("resize", () => {
    if (window.innerWidth > 820 && !runtime.sidebarOpen) {
      runtime.sidebarOpen = true;
      document.body.classList.remove("sidebar-collapsed");
    }
  });
  window.addEventListener("focus", () => {
    if (isLoggedIn() && Date.now() - runtime.lastSync > 45_000) void syncCloud();
  });
  window.addEventListener("beforeunload", () => {
    void saveNow();
  });
}

function attachDragAndDrop(): void {
  const composer = document.querySelector(".composer");
  if (!composer) return;
  composer.addEventListener("dragover", (event) => {
    event.preventDefault();
    composer.classList.add("drop-target");
  });
  composer.addEventListener("dragleave", () => composer.classList.remove("drop-target"));
  composer.addEventListener("drop", (event) => {
    event.preventDefault();
    composer.classList.remove("drop-target");
    const dragEvent = event as DragEvent;
    const files = Array.from(dragEvent.dataTransfer?.files ?? []).filter(
      (file): file is File => file instanceof File && file.type.startsWith("image/"),
    );
    if (files.length) void addImageFiles(files);
  });
}

async function bootstrap(): Promise<void> {
  buildShell();
  attachListeners();
  attachDragAndDrop();
  try {
    await loadState();
  } catch (error) {
    console.error(error);
    toast("读取本地数据失败", "error");
  }
  runtime.loginServer = state.cloudServerUrl || DEFAULT_CLOUD_SERVER;
  runtime.sidebarOpen = window.innerWidth > 820;
  document.body.classList.toggle("sidebar-collapsed", !runtime.sidebarOpen);
  renderAll();
  $("#boot").classList.add("hidden");
  await Promise.all([
    listen<{ requestId: string; text: string }>("ai-delta", (event) => applyDelta(event.payload.requestId, event.payload.text)),
    listen<{ requestId: string }>("ai-done", (event) => {
      const stream = runtime.streaming;
      if (!stream || stream.requestId !== event.payload.requestId) return;
      const conversation = state.conversations.find((item) => item.id === stream.conversationId);
      const message = conversation?.messages.find((item) => item.id === stream.messageId);
      if (!message?.content.trim()) finishStream("ERROR", "服务商没有返回可显示的内容");
      else finishStream("COMPLETE", null);
    }),
    listen<{ requestId: string; message: string }>("ai-error", (event) => {
      const stream = runtime.streaming;
      if (!stream || stream.requestId !== event.payload.requestId) return;
      finishStream("ERROR", event.payload.message);
    }),
  ]);
  if (isLoggedIn()) void syncCloud();
  focusDraft();
}

void bootstrap();
