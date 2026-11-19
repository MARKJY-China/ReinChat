<template>
  <div v-if="booting" class="boot">
    <span class="wordmark"><span class="mark">R</span>ReinChat</span>
    <div class="boot-line"><span></span></div>
    <p>正在载入对话</p>
  </div>
  <LoginView v-else-if="!token" :error="authError" :busy="busy" :initial-server="server" @submit="login" />
  <div v-else class="app" :class="{ 'rail-collapsed': railCollapsed, 'rail-open': railOpen }">
    <aside class="rail">
      <div class="rail-top">
        <span class="wordmark small"><span class="mark">R</span>ReinChat</span>
        <button class="icon-btn" title="新建会话" @click="newConversation">＋</button>
      </div>
      <div class="rail-label">会话</div>
      <nav class="rail-list">
        <div v-for="c in sortedConversations" :key="c.id" class="rail-row" :class="{ active: c.id === activeId, selected: selectedIds.includes(c.id) }">
          <span v-if="selectionMode" class="rail-check" :class="{ on: selectedIds.includes(c.id) }" @click.stop="toggleSelected(c.id)">✓</span>
          <button class="rail-item" @click="selectionMode ? toggleSelected(c.id) : openConversation(c.id)" @contextmenu.prevent="openConvMenu(c, $event)">
            <span class="rail-item-mark">{{ (c.title || '新').slice(0, 1) }}</span>
            <input v-if="renamingId === c.id" ref="renameInput" class="rename-input" v-model="renameDraft" @click.stop @keyup.enter="commitRename(c)" @keyup.esc="renamingId = ''" @blur="commitRename(c)">
            <span v-else class="rail-item-title">{{ c.title || '新会话' }}</span>
            <span class="rail-item-time">{{ formatShort(c.updatedAt) }}</span>
          </button>
          <button v-if="!selectionMode" class="rail-more" title="会话操作" @click.stop="openConvMenu(c, $event)">···</button>
          <span v-if="pinnedIds.includes(c.id)" class="pin-mark">置顶</span>
        </div>
        <p class="rail-empty" v-if="!conversations.length">还没有会话</p>
      </nav>
      <div v-if="selectionMode" class="select-bar">
        <span>已选 {{ selectedIds.length }}</span>
        <button class="quiet-btn small" @click="selectionMode = false; selectedIds = []">取消</button>
        <button class="quiet-btn small danger" :disabled="!selectedIds.length" @click="deleteSelected">删除</button>
      </div>
      <div v-if="convMenu" class="conv-menu" :style="{ top: convMenu.y + 'px', left: convMenu.x + 'px' }" @click.stop>
        <button @click="startRename(convMenu.conversation)">重命名</button>
        <button @click="togglePin(convMenu.conversation)">{{ pinnedIds.includes(convMenu.conversation.id) ? '取消置顶' : '置顶' }}</button>
        <button @click="shareConversation(convMenu.conversation)">分享</button>
        <button @click="startSelection(convMenu.conversation)">多选</button>
        <button class="danger" @click="deleteConversation(convMenu.conversation)">删除</button>
      </div>
      <div class="rail-bottom">
        <button class="user-row" @click="userMenu = !userMenu">
          <span class="avatar">{{ user.slice(0, 1).toUpperCase() }}</span>
          <span class="user-name">{{ user }}<small>LV{{ level }}</small></span>
          <span class="more">···</span>
        </button>
        <div class="popover" v-if="userMenu">
          <button @click="openProviderManager(); userMenu = false">服务商管理</button>
          <a href="/admin">管理台</a>
          <button @click="logout">退出登录</button>
        </div>
      </div>
    </aside>

    <main class="main">
      <header class="topbar">
        <button class="icon-btn rail-toggle" :title="railCollapsed ? '展开会话列表' : '收起会话列表'" @click="toggleRail">☰</button>
        <div class="crumb"><span class="crumb-root">ReinChat</span><span class="crumb-sep">/</span><span>{{ active ? (active.title || '新会话') : '新会话' }}</span></div>
        <div class="top-actions">
          <span class="sync-dot" title="实时同步"></span>
          <button class="icon-btn" title="界面设置" @click="settingsOpen = true">⚙</button>
          <button class="icon-btn theme-toggle" :title="dark ? '切换浅色' : '切换暗色'" @click="toggleTheme">{{ dark ? '☀' : '☾' }}</button>
          <button class="quiet-btn small" @click="refresh">刷新</button>
        </div>
      </header>

      <section class="thread" ref="thread" @scroll="onThreadScroll">
        <div class="thread-inner">
          <template v-if="active">
            <MessageItem v-for="(m, index) in active.messages" :key="m.id" :message="m" :streaming="sending" :stream-phase="streamPhase" @retry="retry(m)" @edit="beginEdit" @rewind="rewind(index)" @stop="stopGeneration" />
            <div class="welcome" v-if="!active.messages.length">
              <p class="eyebrow">New conversation</p>
              <h1>今天想聊什么？</h1>
              <p class="welcome-sub">在下方输入消息。选择你的模型，或者直接开始。</p>
            </div>
          </template>
          <div class="welcome" v-else>
            <p class="eyebrow">ReinChat</p>
            <h1>开始一段对话。</h1>
            <p class="welcome-sub">从左侧选择会话，或新建一个。</p>
            <button class="primary-btn" @click="newConversation">新建会话<span class="arrow">→</span></button>
          </div>
        </div>
      </section>

      <ComposerBar
        :provider-id="providerId" :model="model" :reasoning-effort="reasoningEffort" :search="search" :draft="draft" :sending="sending" :level="level"
        :provider-options="providerOptions" :model-options="modelOptions" :reasoning-options="reasoningOptions"
        :pdf-name="pdfAttachment ? pdfAttachment.name : ''" :pdf-busy="pdfBusy" :pdf-error="pdfError" :image-previews="imageAttachments"
        @update:providerId="setProvider" @update:model="model = $event" @update:reasoningEffort="reasoningEffort = $event"
        @update:search="search = $event" @update:draft="draft = $event" @send="send" @stop="stopGeneration"
        @attach-pdf="attachPdf" @remove-pdf="pdfAttachment = null" @paste-files="handlePastedFiles" @remove-image="imageAttachments.splice($event, 1)" />
    </main>

    <div v-if="settingsOpen" class="modal-mask" @click.self="settingsOpen = false">
      <div class="modal-card settings-card">
        <div class="modal-head"><div><h2>界面设置</h2><p class="muted">只影响当前浏览器</p></div><button class="icon-btn" @click="settingsOpen = false">×</button></div>
        <div class="setting-row">
          <div><strong>字体大小</strong><p class="muted">整个界面的缩放比例</p></div>
          <span class="setting-value">{{ Math.round(fontScale * 100) }}%</span>
        </div>
        <input class="scale-range" type="range" min="0.85" max="1.25" step="0.05" v-model.number="fontScale" @input="applyFontScale">
        <div class="scale-labels"><span>小</span><span>标准</span><span>大</span></div>
        <div class="setting-row theme-row">
          <div><strong>主题</strong><p class="muted">跟随系统或手动选择</p></div>
          <button class="quiet-btn small" @click="toggleTheme">{{ dark ? '切换到浅色' : '切换到暗色' }}</button>
        </div>
        <div class="modal-actions"><button class="quiet-btn" @click="resetFontScale">恢复标准</button><button class="primary-btn" @click="settingsOpen = false">完成</button></div>
      </div>
    </div>

    <div v-if="providerModal" class="modal-mask" @click.self="providerModal = false">
      <div class="modal-card">
        <div class="modal-head">
          <div><h2>服务商</h2><p class="muted">用于服务端 Agent 的模型服务</p></div>
          <button class="icon-btn" @click="providerModal = false">×</button>
        </div>
        <template v-if="!providerDraft">
          <div class="provider-list">
            <div v-for="p in providerRecords" :key="p.id" class="provider-row">
              <div class="provider-avatar">{{ p.name.slice(0, 1).toUpperCase() }}</div>
              <div class="provider-info"><strong>{{ p.name }}</strong><span>{{ p.baseUrl }}</span><small>{{ p.model }} · {{ p.protocol }}</small></div>
              <button class="quiet-btn small" @click="editProvider(p)">编辑</button>
              <button class="quiet-btn small danger" @click="deleteProvider(p)">删除</button>
            </div>
            <p class="rail-empty" v-if="!providerRecords.length">暂无服务商</p>
          </div>
          <div class="modal-actions"><button class="primary-btn" @click="newProvider">添加服务商</button></div>
        </template>
        <template v-else>
          <div class="form-grid">
            <label class="field">名称<input v-model.trim="providerDraft.name" placeholder="DeepSeek"></label>
            <label class="field">协议
              <span class="segmented">
                <button type="button" :class="{ active: providerDraft.protocol === 'CHAT_COMPLETIONS' }" @click="providerDraft.protocol = 'CHAT_COMPLETIONS'">Chat</button>
                <button type="button" :class="{ active: providerDraft.protocol === 'RESPONSES' }" @click="providerDraft.protocol = 'RESPONSES'">Responses</button>
              </span>
            </label>
            <label class="field wide">Base URL<input v-model.trim="providerDraft.baseUrl" placeholder="https://api.deepseek.com/v1"></label>
            <label class="field wide">API Key<input v-model.trim="providerDraft.apiKey" type="password"></label>
            <div class="model-manager wide">
              <div class="model-manager-head"><span>模型目录</span><button class="quiet-btn small" :disabled="providerModelsLoading" @click="fetchProviderModels">{{ providerModelsLoading ? '获取中…' : '获取可用模型' }}</button></div>
              <div class="model-row" v-for="(m, index) in providerDraft.models" :key="index">
                <input v-model.trim="m.id" placeholder="模型 ID">
                <input v-model.trim="m.name" placeholder="显示名称">
                <input v-model.number="m.contextWindow" type="number" placeholder="上下文">
                <input v-model.number="m.maxOutputTokens" type="number" placeholder="最大输出">
                <button class="icon-btn" title="删除模型" @click="providerDraft.models.splice(index, 1)">×</button>
              </div>
              <button class="quiet-btn small add-model" @click="providerDraft.models.push({ id: '', name: '', contextWindow: 0, maxOutputTokens: 0 })">＋ 添加模型</button>
            </div>
            <label class="field wide">系统提示词<textarea v-model="providerDraft.systemPrompt" rows="3"></textarea></label>
          </div>
          <p class="auth-error" v-if="providerError">{{ providerError }}</p>
          <div class="modal-actions"><button class="quiet-btn" @click="providerDraft = null">返回</button><button class="primary-btn" :disabled="providerBusy" @click="saveProvider">{{ providerBusy ? '保存中…' : '保存' }}</button></div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import LoginView from './components/LoginView.vue';
import MessageItem from './components/MessageItem.vue';
import ComposerBar from './components/ComposerBar.vue';
import { Api } from './lib/api';
import { uuid } from './lib/markdown';
import { extractPdfText } from './lib/pdf';

const TOKEN = 'reinchat_vue2_token';
const USER = 'reinchat_vue2_user';
const SERVER = 'reinchat_vue2_server';

export default {
  name: 'App',
  components: { LoginView, MessageItem, ComposerBar },
  data() {
    return {
      token: localStorage.getItem(TOKEN) || '',
      booting: true,
      user: localStorage.getItem(USER) || '',
      server: localStorage.getItem(SERVER) || window.location.origin,
      authError: '', busy: false,
      conversations: [], active: null, activeId: '',
      providers: [], providerRecords: [], providerId: '', model: '', reasoningEffort: 'high', search: true,
      draft: '', level: 1, agentAvailable: false, userMenu: false, railOpen: false,
      railCollapsed: localStorage.getItem('reinchat_rail_collapsed') === '1',
      dark: localStorage.getItem('reinchat_theme') === 'dark' || (localStorage.getItem('reinchat_theme') !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches),
      conversationCache: {}, streams: {}, applyingSettings: false, settingsTimer: null,
      providerModal: false, providerDraft: null, providerBusy: false, providerError: '',
      providerModelsLoading: false, settingsOpen: false,
      fontScale: Number(localStorage.getItem('reinchat_font_scale') || 1),
      pdfAttachment: null, pdfBusy: false, pdfError: '', imageAttachments: [], followBottom: true,
      convMenu: null, pinnedIds: JSON.parse(localStorage.getItem('reinchat_pins') || '[]'),
      selectionMode: false, selectedIds: [], renamingId: '', renameDraft: '',
    };
  },
  computed: {
    api() { return new Api(this.server, this.token); },
    webBase() { return window.location.pathname.startsWith('/v2/') ? '/v2/' : '/'; },
    sending() { return !!(this.activeId && this.streams[this.activeId]); },
    streamPhase() { return (this.activeId && this.streams[this.activeId] && this.streams[this.activeId].phase) || ''; },
    providerOptions() { return this.providers.map((p) => ({ value: p.id, label: p.name })); },
    sortedConversations() {
      return [...this.conversations].sort((a, b) => {
        const ap = this.pinnedIds.includes(a.id), bp = this.pinnedIds.includes(b.id);
        if (ap !== bp) return ap ? -1 : 1;
        return (b.updatedAt || 0) - (a.updatedAt || 0);
      });
    },
    modelOptions() {
      const provider = this.providers.find((p) => p.id === this.providerId);
      if (provider && provider.models && provider.models.length) {
        return provider.models.filter((m) => m.id).map((m) => ({ value: m.id, label: m.name || m.id }));
      }
      return ((provider && provider.model) || '').split(',').map((m) => m.trim()).filter(Boolean).map((m) => ({ value: m, label: m }));
    },
    reasoningOptions() {
      return [{ value: '', label: '思考：自动' }, { value: 'low', label: '思考：低' }, { value: 'high', label: '思考：高' }, { value: 'max', label: '思考：最高' }];
    },
  },
  watch: {
    providerId() {
      const provider = this.providers.find((p) => p.id === this.providerId);
      const first = provider && provider.models && provider.models.length
        ? provider.models[0].id
        : ((provider && provider.model) || '').split(',')[0].trim();
      if (first && !this.modelOptions.some((m) => m.value === this.model)) this.model = first;
      if (!this.applyingSettings) this.scheduleSettings();
    },
    model() { if (!this.applyingSettings) this.scheduleSettings(); },
    reasoningEffort() { if (!this.applyingSettings) this.scheduleSettings(); },
    search() { if (!this.applyingSettings) this.scheduleSettings(); },
  },
  async mounted() {
    this.applyTheme();
    this.applyFontScale();
    document.addEventListener('click', this.closeConvMenu);
    document.addEventListener('paste', this.onGlobalPaste);
    window.addEventListener('popstate', this.openFromLocation);
    await this.waitForFonts();
    if (this.token) {
      await this.refresh();
      await this.openFromLocation();
    }
    this.booting = false;
  },
  beforeDestroy() {
    document.removeEventListener('click', this.closeConvMenu);
    window.removeEventListener('popstate', this.openFromLocation);
    document.removeEventListener('paste', this.onGlobalPaste);
  },
  updated() {
    if (this.followBottom) this.scrollThreadToBottom();
  },
  methods: {
    async login(payload) {
      this.busy = true; this.authError = '';
      try {
        const api = new Api(payload.server, '');
        const data = await api.auth(payload.mode === 'register' ? 'register' : 'login', payload.username, payload.password);
        this.token = data.token; this.user = data.user.username; this.server = payload.server; this.level = data.user.level || 1;
        localStorage.setItem(TOKEN, this.token); localStorage.setItem(USER, this.user); localStorage.setItem(SERVER, this.server);
        await this.refresh();
        await this.openFromLocation();
      } catch (e) { this.authError = e.message; } finally { this.busy = false; }
    },
    logout() {
      localStorage.removeItem(TOKEN); localStorage.removeItem(USER); localStorage.removeItem(SERVER);
      this.token = ''; this.user = ''; this.active = null; this.activeId = ''; this.conversations = [];
      if (window.location.pathname !== this.webBase) window.history.pushState({}, '', this.webBase);
    },
    async refresh() {
      try {
        const [list, account, providers] = await Promise.all([this.api.conversations(), this.api.account(), this.api.relayProviders()]);
        this.conversations = list.conversations || [];
        this.level = account.user.level || 1; this.agentAvailable = !!account.agentAvailable;
        this.providers = providers.providers || [];
        const full = await this.api.providers();
        this.providerRecords = full.providers || [];
        if (!this.providerId && this.providers.length) this.providerId = this.providers[0].id;
        if (this.activeId) await this.openConversation(this.activeId);
      } catch (e) { this.authError = e.message; }
    },
    conversationIdFromPath() {
      const path = window.location.pathname;
      if (!path.startsWith(this.webBase) || path === this.webBase) return '';
      let rest = path.slice(this.webBase.length).replace(/\/+$/, '');
      if (!rest || rest === 'index.html' || rest.includes('/') || rest.includes('.')) return '';
      try { return decodeURIComponent(rest); } catch (e) { return ''; }
    },
    async openFromLocation() {
      const pathId = this.conversationIdFromPath();
      const hash = window.location.hash.match(/conversation=([^&]+)/);
      const id = pathId || (hash ? decodeURIComponent(hash[1]) : '');
      if (!id) { this.active = null; this.activeId = ''; return; }
      if (id === this.activeId) return;
      await this.openConversation(id);
    },
    setConversationUrl(id) {
      const target = this.webBase + encodeURIComponent(id);
      if (window.location.pathname !== target) {
        window.history.pushState({ conversationId: id }, '', target);
      }
    },
    async openConversation(id) {
      this.activeId = id;
      this.railOpen = false;
      const cached = this.conversationCache[id];
      if (cached && this.streams[id]) {
        this.active = cached;
        this.applySettings(cached);
        this.setConversationUrl(id);
        return;
      }
      try {
        const data = await this.api.conversation(id);
        await this.hydrateImages(data);
        this.$set(this.conversationCache, id, data);
        this.active = data;
        this.applySettings(data);
        this.setConversationUrl(id);
      } catch (e) { this.authError = e.message; }
    },
    async hydrateImages(conversation) {
      for (const message of conversation.messages || []) {
        for (const image of message.images || []) {
          if (!image.url && image.id) {
            try { const url = await this.api.fileUrl(image.id); if (url) this.$set(image, 'url', url); } catch (e) { /* ignore */ }
          }
        }
      }
    },
    openConvMenu(conversation, event) {
      if (this.selectionMode) return;
      const width = 150, height = 190;
      const x = Math.min(event.clientX, window.innerWidth - width - 10);
      const y = Math.min(event.clientY, window.innerHeight - height - 10);
      this.convMenu = { conversation, x: Math.max(8, x), y: Math.max(8, y) };
    },
    closeConvMenu() { this.convMenu = null; },
    startRename(conversation) {
      this.renamingId = conversation.id;
      this.renameDraft = conversation.title === '新会话' ? '' : conversation.title;
      this.convMenu = null;
      this.$nextTick(() => {
        const input = this.$refs.renameInput;
        const el = Array.isArray(input) ? input[0] : input;
        if (el) { el.focus(); el.select(); }
      });
    },
    async commitRename(conversation) {
      if (this.renamingId !== conversation.id) return;
      const title = (this.renameDraft || '').trim();
      this.renamingId = '';
      if (!title || title === conversation.title) return;
      try {
        const saved = await this.api.renameConversation(conversation.id, title);
        const item = this.conversations.find((c) => c.id === saved.id);
        if (item) item.title = saved.title;
        if (this.activeId === saved.id && this.active) this.active.title = saved.title;
      } catch (e) { this.authError = e.message; }
    },
    togglePin(conversation) {
      if (this.pinnedIds.includes(conversation.id)) this.pinnedIds = this.pinnedIds.filter((id) => id !== conversation.id);
      else this.pinnedIds = [conversation.id, ...this.pinnedIds];
      localStorage.setItem('reinchat_pins', JSON.stringify(this.pinnedIds));
      this.convMenu = null;
    },
    async shareConversation(conversation) {
      this.convMenu = null;
      const url = this.server.replace(/\/+$/, '') + this.webBase + encodeURIComponent(conversation.id);
      if (navigator.share) {
        try { await navigator.share({ title: conversation.title || 'ReinChat', text: conversation.title || 'ReinChat 会话', url }); return; } catch (e) { /* fall through */ }
      }
      try { await navigator.clipboard.writeText(url); } catch (e) { /* ignore */ }
    },
    startSelection(conversation) {
      this.selectionMode = true;
      this.selectedIds = conversation ? [conversation.id] : [];
      this.convMenu = null;
    },
    toggleSelected(id) {
      this.selectedIds = this.selectedIds.includes(id)
        ? this.selectedIds.filter((v) => v !== id)
        : [...this.selectedIds, id];
    },
    async deleteConversation(conversation) {
      this.convMenu = null;
      if (!window.confirm('删除会话「' + (conversation.title || '新会话') + '」？此操作不可恢复。')) return;
      try {
        await this.api.deleteConversation(conversation.id);
        this.conversations = this.conversations.filter((c) => c.id !== conversation.id);
        if (this.activeId === conversation.id) {
          this.active = null; this.activeId = '';
          if (window.location.pathname !== this.webBase) window.history.pushState({}, '', this.webBase);
        }
      } catch (e) { this.authError = e.message; }
    },
    async deleteSelected() {
      if (!this.selectedIds.length || !window.confirm('删除选中的 ' + this.selectedIds.length + ' 个会话？')) return;
      try {
        await Promise.all(this.selectedIds.map((id) => this.api.deleteConversation(id)));
        this.conversations = this.conversations.filter((c) => !this.selectedIds.includes(c.id));
        if (this.selectedIds.includes(this.activeId)) {
          this.active = null; this.activeId = '';
          if (window.location.pathname !== this.webBase) window.history.pushState({}, '', this.webBase);
        }
        this.selectedIds = []; this.selectionMode = false;
      } catch (e) { this.authError = e.message; }
    },
    setProvider(id) {
      this.providerId = id;
      const provider = this.providers.find((p) => p.id === id);
      this.model = provider && provider.models && provider.models.length
        ? provider.models[0].id
        : ((provider && provider.model) || '').split(',')[0].trim();
    },
    applySettings(conversation) {
      const settings = conversation && conversation.settings;
      if (!settings) return;
      this.applyingSettings = true;
      if (settings.providerId) this.providerId = settings.providerId;
      if (settings.model) this.model = settings.model;
      if (settings.reasoningEffort) this.reasoningEffort = settings.reasoningEffort;
      this.search = settings.search !== false;
      this.$nextTick(() => { this.applyingSettings = false; });
    },
    scheduleSettings() {
      if (!this.active || !this.active.version || this.sending) return;
      this.active.settings = { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search };
      if (this.settingsTimer) clearTimeout(this.settingsTimer);
      this.settingsTimer = setTimeout(() => { this.settingsTimer = null; this.persist(this.active); }, 350);
    },
    openProviderManager() { this.providerModal = true; this.providerDraft = null; this.providerError = ''; this.refresh(); },
    newProvider() {
      this.providerDraft = { id: uuid(), name: '', protocol: 'CHAT_COMPLETIONS', baseUrl: '', apiKey: '', model: '', models: [], systemPrompt: '' };
      this.providerError = '';
    },
    editProvider(p) {
      const models = p.models && p.models.length
        ? p.models.map((m) => ({ ...m }))
        : (p.model || '').split(',').filter(Boolean).map((id) => ({ id: id.trim(), name: id.trim(), contextWindow: 0, maxOutputTokens: 0 }));
      this.providerDraft = { ...p, models };
      this.providerError = '';
    },
    async fetchProviderModels() {
      if (!this.providerDraft || this.providerModelsLoading) return;
      this.providerModelsLoading = true; this.providerError = '';
      try {
        const data = await this.api.request('/v1/provider-models', {
          method: 'POST',
          body: { baseUrl: this.providerDraft.baseUrl, apiKey: this.providerDraft.apiKey || '' },
        });
        const existing = new Set((this.providerDraft.models || []).map((m) => m.id));
        for (const model of data.models || []) {
          if (!existing.has(model.id)) this.providerDraft.models.push({ id: model.id, name: model.name || model.id, contextWindow: 0, maxOutputTokens: 0 });
        }
      } catch (e) { this.providerError = e.message; } finally { this.providerModelsLoading = false; }
    },
    async saveProvider() {
      if (!this.providerDraft || this.providerBusy) return;
      const p = this.providerDraft;
      p.models = (p.models || []).filter((m) => m.id.trim()).map((m) => ({ ...m, id: m.id.trim(), name: (m.name || m.id).trim() }));
      if (!p.name.trim() || !p.baseUrl.trim() || !p.models.length) { this.providerError = '名称、Base URL、至少一个模型不能为空'; return; }
      this.providerBusy = true; this.providerError = '';
      try {
        await this.api.putProvider({
          id: p.id, name: p.name.trim(), protocol: p.protocol, baseUrl: p.baseUrl.trim(), apiKey: p.apiKey || '',
          model: p.models.map((m) => m.id).join(','), models: p.models,
          systemPrompt: p.systemPrompt || '', temperature: null, maxTokens: null,
        });
        this.providerDraft = null;
        await this.refresh();
      } catch (e) { this.providerError = e.message; } finally { this.providerBusy = false; }
    },
    async deleteProvider(p) {
      if (!window.confirm('删除服务商「' + p.name + '」？')) return;
      try { await this.api.deleteProvider(p.id); if (this.providerId === p.id) this.providerId = ''; await this.refresh(); } catch (e) { this.providerError = e.message; }
    },
    waitForFonts() {
      if (!document.fonts) return Promise.resolve();
      return Promise.race([
        document.fonts.load('16px "HarmonyOS Sans SC"').then(() => document.fonts.ready),
        new Promise((resolve) => setTimeout(resolve, 1200)),
      ]).catch(() => {});
    },
    applyTheme() { document.documentElement.classList.toggle('dark', this.dark); },
    applyFontScale() {
      document.documentElement.style.setProperty('--ui-scale', String(this.fontScale));
      localStorage.setItem('reinchat_font_scale', String(this.fontScale));
    },
    resetFontScale() { this.fontScale = 1; this.applyFontScale(); },
    toggleTheme() {
      this.dark = !this.dark;
      localStorage.setItem('reinchat_theme', this.dark ? 'dark' : 'light');
      this.applyTheme();
    },
    toggleRail() {
      if (window.innerWidth <= 800) { this.railOpen = !this.railOpen; return; }
      this.railCollapsed = !this.railCollapsed;
      localStorage.setItem('reinchat_rail_collapsed', this.railCollapsed ? '1' : '0');
    },
    newConversation() {
      this.railOpen = false;
      if (window.location.pathname !== this.webBase) window.history.pushState({}, '', this.webBase);
      const now = Date.now();
      this.active = { id: uuid(), title: '新会话', createdAt: now, updatedAt: now, version: 0, messages: [], settings: { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search } };
      this.activeId = this.active.id; this.draft = '';
    },
    beginEdit(message) { this.newConversation(); this.active.messages.push({ ...message, id: uuid() }); this.draft = message.content; },
    async rewind(index) { if (!this.active || index >= this.active.messages.length - 1) return; this.active.messages = this.active.messages.slice(0, index + 1); await this.persist(); },
    async retry(target) {
      if (this.sending || !this.active || !target) return;
      const index = this.active.messages.findIndex((m) => m.id === target.id);
      if (index < 0) return;
      let userIndex = -1; for (let i = index - 1; i >= 0; i--) if (this.active.messages[i].role === 'user') { userIndex = i; break; }
      if (userIndex < 0) return;
      const previous = this.active.messages[userIndex];
      const text = previous.content;
      const pdf = previous.pdfText ? { name: previous.pdfName, text: previous.pdfText } : null;
      this.active.messages = this.active.messages.slice(0, userIndex + 1);
      await this.sendText(text, true, pdf);
    },
    async send() {
      const text = this.draft.trim();
      if ((!text && !this.pdfAttachment && !this.imageAttachments.length) || this.sending || !this.providerId) return;
      const pdf = this.pdfAttachment;
      const previews = this.imageAttachments.slice();
      this.draft = ''; this.pdfAttachment = null; this.imageAttachments = [];
      let images = [];
      try {
        images = await Promise.all(previews.map(async (item) => {
          const uploaded = await this.api.uploadFile(item.file);
          return { id: uploaded.id, remoteId: uploaded.id, mimeType: uploaded.mimeType, width: 0, height: 0, url: item.url };
        }));
      } catch (e) { this.authError = '图片上传失败：' + e.message; return; }
      this.sendText(text || (pdf ? '请阅读并分析这份 PDF。' : '请分析这张图片。'), false, pdf, images);
    },
    onThreadScroll() {
      const el = this.$refs.thread;
      if (!el) return;
      this.followBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80;
    },
    scrollThreadToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.thread;
        if (el) el.scrollTop = el.scrollHeight;
      });
    },
    onGlobalPaste(event) {
      if (event.target && event.target.closest && event.target.closest('.composer')) return;
      const files = event.clipboardData ? Array.from(event.clipboardData.files || []) : [];
      if (files.length) { event.preventDefault(); this.handlePastedFiles(files); }
    },
    async handlePastedFiles(files) {
      for (const file of files) {
        if (file.type === 'application/pdf' || /\.pdf$/i.test(file.name)) await this.attachPdf(file);
        else if (/^image\/(png|jpe?g|webp)$/.test(file.type)) {
          this.imageAttachments.push({ file, name: file.name || '粘贴的图片', url: URL.createObjectURL(file) });
        }
      }
    },
    async attachPdf(file) {
      if (!file || this.pdfBusy) return;
      if (file.type !== 'application/pdf' && !/\.pdf$/i.test(file.name)) { this.authError = '请选择 PDF 文件'; return; }
      if (file.size > 40 * 1024 * 1024) { this.authError = 'PDF 不能超过 40MB'; return; }
      this.pdfBusy = true; this.authError = ''; this.pdfError = '';
      try {
        const parsed = await extractPdfText(file);
        this.pdfAttachment = parsed;
        if (!parsed.text) this.pdfError = 'PDF 中没有可提取的文字（可能是扫描件）';
      } catch (e) { this.pdfError = 'PDF 解析失败：' + (e.message || '未知错误'); } finally { this.pdfBusy = false; }
    },
    async sendText(text, retry, pdf, images) {
      if (!this.active) this.newConversation();
      const conversation = this.active;
      conversation.settings = { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search };
      this.$set(this.conversationCache, conversation.id, conversation);
      const now = Date.now();
      if (!retry) conversation.messages.push({ id: uuid(), role: 'user', content: text, pdfName: pdf ? pdf.name : '', pdfText: pdf ? pdf.text : '', images: images || [], createdAt: now, status: 'COMPLETE', error: null });
      const assistant = { id: uuid(), role: 'assistant', content: '', reasoning: '', segments: [], model: this.model, toolCalls: [], images: [], createdAt: now + 1, status: 'COMPLETE', error: null, streaming: true };
      conversation.messages.push(assistant);
      this.followBottom = true;
      this.scrollThreadToBottom();
      const controller = new AbortController();
      this.$set(this.streams, conversation.id, { assistantId: assistant.id, phase: 'waiting', controller, cancelled: false });
      const stream = this.streams[conversation.id];
      try {
        if (this.level >= 2 && this.agentAvailable) await this.streamDsh(conversation, assistant, text, stream, pdf);
        else await this.streamRelay(conversation, assistant, stream, pdf);
        await this.persist(conversation);
        this.maybeGenerateTitle(conversation).catch(() => {});
      } catch (e) {
        if (e.name === 'AbortError' || stream.cancelled) {
          assistant.status = 'CANCELLED';
        } else {
          assistant.error = e.message;
          assistant.status = 'ERROR';
        }
      } finally {
        assistant.streaming = false;
        if (this.streams[conversation.id] === stream) this.$delete(this.streams, conversation.id);
      }
    },
    stopGeneration() {
      const id = this.activeId;
      const stream = id && this.streams[id];
      if (!stream) return;
      stream.cancelled = true;
      try { stream.controller?.abort(); } catch (e) { /* ignore */ }
      if (stream.jobId) {
        this.api.request('/v1/relay/jobs/' + encodeURIComponent(stream.jobId) + '/cancel', { method: 'POST' }).catch(() => {});
      }
      const conversation = this.active;
      const assistant = conversation && conversation.messages.find((m) => m.id === stream.assistantId);
      if (assistant) { assistant.streaming = false; assistant.status = 'CANCELLED'; }
      this.$delete(this.streams, id);
    },
    async maybeGenerateTitle(conversation) {
      if (!conversation || (conversation.title && conversation.title !== '新会话')) return;
      const users = (conversation.messages || []).filter((m) => m.role === 'user');
      if (!users.length || !users[0].content) return;
      const settings = conversation.settings || {};
      const fallback = users[0].content.replace(/\s+/g, ' ').trim().slice(0, 20);
      try {
        const data = await this.api.request('/v1/conversations/' + encodeURIComponent(conversation.id) + '/title', {
          method: 'POST',
          body: { providerId: settings.providerId || this.providerId, model: settings.model || this.model, text: users[0].content },
        });
        if (data && data.title) conversation.title = data.title;
      } catch (e) {
        conversation.title = fallback || '新会话';
      }
      if (this.activeId === conversation.id) this.active.title = conversation.title;
      await this.persist(conversation);
    },
    appendReasoning(assistant, text) {
      if (!text) return;
      const segments = (assistant.segments || []).slice();
      const last = segments[segments.length - 1];
      if (last && last.type === 'reasoning') last.text += text;
      else segments.push({ type: 'reasoning', text });
      this.$set(assistant, 'segments', segments);
      assistant.reasoning = (assistant.reasoning || '') + text;
      if (this.followBottom) this.scrollThreadToBottom();
    },
    upsertTool(assistant, name, status, detail) {
      const segments = (assistant.segments || []).slice();
      const label = name === 'web_search' ? '正在搜索网页' : name === 'fetch_url' ? '正在浏览网页' : '正在使用工具';
      let segment = segments.slice().reverse().find((s) => s.type === 'tool' && s.name === name && s.status === 'running');
      if (!segment) {
        segment = { type: 'tool', name, label, status: 'running', detail: '' };
        segments.push(segment);
      }
      segment.status = status;
      if (detail) segment.detail = String(detail).slice(0, 4000);
      if (status === 'done') segment.label = name === 'web_search' ? '搜索网页' : name === 'fetch_url' ? '浏览网页' : name;
      this.$set(assistant, 'segments', segments);
      if (this.followBottom) this.scrollThreadToBottom();
    },
    async responseError(res, label) {
      const raw = await res.text();
      let detail = raw;
      try {
        const data = JSON.parse(raw);
        detail = (data.error && (data.error.message || data.error.code)) || data.message || raw;
      } catch (e) { /* keep raw */ }
      const error = new Error(label + ' ' + res.status + (detail ? '：' + String(detail).slice(0, 1200) : ''));
      error.status = res.status;
      error.detail = raw.slice(0, 4000);
      return error;
    },
    async streamRelay(conversation, assistant, stream, pdf) {
      const settings = conversation.settings || {};
      const history = conversation.messages.filter((m) => m !== assistant && m.content).map((m) => ({
        id: m.id, role: m.role,
        content: m.pdfText ? `${m.content}\n\n[附件 PDF：${m.pdfName}]\n${m.pdfText}` : m.content,
        images: (m.images || []).map((img) => ({ id: img.id, remoteId: img.remoteId, mimeType: img.mimeType, width: img.width, height: img.height })), createdAt: m.createdAt, status: 'COMPLETE', error: null,
      }));
      const job = await this.api.request('/v1/relay/jobs', { method: 'POST', body: { providerId: settings.providerId || this.providerId, conversationId: conversation.id, model: settings.model || this.model, reasoningEffort: settings.reasoningEffort || this.reasoningEffort, search: settings.search !== false, messages: history } });
      stream.jobId = job.id;
      const res = await fetch(this.api.base() + '/v1/relay/jobs/' + encodeURIComponent(job.id) + '/events', { headers: { Authorization: 'Bearer ' + this.token }, signal: stream.controller.signal });
      if (!res.ok) throw await this.responseError(res, 'relay');
      const reader = res.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
      while (true) {
        const part = await reader.read(); if (part.done) break;
        buffer += decoder.decode(part.value, { stream: true });
        let cut;
        while ((cut = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, cut); buffer = buffer.slice(cut + 2);
          const data = block.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('');
          if (!data) continue;
          const event = JSON.parse(data);
          if (event.type === 'snapshot') {
            if (Array.isArray(event.tools)) {
              event.tools.forEach((tool) => this.upsertTool(assistant, tool.tool || '', tool.status || 'running', tool.detail));
            }
            if (event.text) { assistant.content = event.text; stream.phase = 'streaming'; }
            if (event.reasoning && (!assistant.segments || !assistant.segments.length)) this.appendReasoning(assistant, event.reasoning);
            if (event.error) throw new Error(String(event.error).slice(0, 4000));
          }
          if (event.type === 'delta') { assistant.content += event.text || ''; stream.phase = 'streaming'; }
          if (event.type === 'reasoning') { this.appendReasoning(assistant, event.text || ''); stream.phase = 'streaming'; }
          if (event.type === 'tool') { this.upsertTool(assistant, event.tool || '', event.status || 'running', event.detail); }
          if (event.type === 'error') throw new Error((event.error || 'relay failed') + (event.status ? '（' + event.status + '）' : ''));
          if (event.type === 'status' && (event.status === 'failed' || event.status === 'cancelled')) {
            throw new Error(event.error || (event.status === 'cancelled' ? '生成已取消' : '生成失败'));
          }
          if (event.type === 'status' && event.status === 'completed') return;
        }
      }
      if (!assistant.content) throw new Error('连接已中断，未收到模型输出');
    },
    async streamDsh(conversation, assistant, text, stream, pdf) {
      const settings = conversation.settings || {};
      const promptText = pdf ? `${text}\n\n[附件 PDF：${pdf.name}]\n${pdf.text}` : text;
      const res = await fetch(this.api.base() + '/v1/agent/run', { method: 'POST', headers: { Authorization: 'Bearer ' + this.token, 'Content-Type': 'application/json' }, signal: stream.controller.signal, body: JSON.stringify({ providerId: settings.providerId || this.providerId, model: settings.model || this.model, reasoningEffort: settings.reasoningEffort || this.reasoningEffort, conversationId: conversation.id, text: promptText }) });
      if (!res.ok) throw await this.responseError(res, 'DSH');
      const reader = res.body.getReader(); const decoder = new TextDecoder(); let buffer = '';
      while (true) {
        const part = await reader.read(); if (part.done) break;
        buffer += decoder.decode(part.value, { stream: true });
        let cut;
        while ((cut = buffer.indexOf('\n\n')) >= 0) {
          const block = buffer.slice(0, cut); buffer = buffer.slice(cut + 2);
          const name = (block.split('\n').find((l) => l.startsWith('event:')) || '').slice(6).trim();
          const data = block.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trim()).join('');
          if (!data) continue;
          const event = JSON.parse(data);
          if (name === 'frame' && event.type === 'chunk' && event.chunk) {
            if (event.chunk.type === 'text-delta') { assistant.content += event.chunk.text || ''; stream.phase = 'streaming'; }
            if (event.chunk.type === 'reasoning-delta') { this.appendReasoning(assistant, event.chunk.text || ''); stream.phase = 'streaming'; }
          }
          if (name === 'event' && event.type === 'tool/call') {
            (assistant.toolCalls = assistant.toolCalls || []).push({ callId: String(event.data?.callId || ''), name: String(event.data?.name || 'tool'), status: 'running' });
            this.upsertTool(assistant, String(event.data?.name || 'tool'), 'running', '');
          }
          if (name === 'event' && event.type === 'tool/result') {
            this.upsertTool(assistant, String(event.data?.name || 'tool'), 'done', (event.data?.message?.content || []).map((b) => b.text || '').join(''));
          }
          if (name === 'done') return;
          if (name === 'error') throw new Error((event.message || 'DSH failed') + (event.stderr ? '\n' + String(event.stderr).slice(-2000) : ''));
        }
      }
    },
    async persist(target) {
      const c = target || this.active;
      if (!c) return;
      const settings = c.settings || { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search };
      const body = { title: c.title || '新会话', createdAt: c.createdAt, updatedAt: Date.now(), messages: c.messages.map((m) => ({ id: m.id, role: m.role, content: m.content || '', reasoning: m.reasoning || '', segments: m.segments || [], pdfName: m.pdfName || '', pdfText: m.pdfText || '', model: m.model || '', images: (m.images || []).map((img) => ({ id: img.id, remoteId: img.remoteId, mimeType: img.mimeType, width: img.width, height: img.height })), createdAt: m.createdAt, status: m.status || 'COMPLETE', error: m.error || null })), settings };
      try {
        const saved = !c.version
          ? await this.api.createConversation({ id: c.id, ...body })
          : await this.api.updateConversation(c.id, { ...body, baseVersion: c.version });
        c.version = saved.version;
        c.createdAt = saved.createdAt;
        c.updatedAt = saved.updatedAt;
        const localById = {};
        (c.messages || []).forEach((m) => { localById[m.id] = m; });
        (saved.messages || []).forEach((m) => {
          const local = localById[m.id];
          if (local && local.images) {
            m.images = (m.images || []).map((img) => {
              const old = local.images.find((x) => x.id === img.id && x.url);
              return old ? { ...img, url: old.url } : img;
            });
          }
        });
        await this.hydrateImages(saved);
        this.$set(this.conversationCache, saved.id, saved);
        if (this.activeId === saved.id) { this.active = saved; this.setConversationUrl(saved.id); }
        await this.refresh();
      } catch (e) { this.authError = e.message; }
    },
    formatShort(ms) { if (!ms) return ''; return new Date(ms).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }); },
  },
};
</script>
