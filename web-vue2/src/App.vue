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
        <button v-for="c in conversations" :key="c.id" class="rail-item" :class="{ active: c.id === activeId }" @click="openConversation(c.id)">
          <span class="rail-item-mark">{{ (c.title || '新').slice(0, 1) }}</span>
          <span class="rail-item-title">{{ c.title || '新会话' }}</span>
          <span class="rail-item-time">{{ formatShort(c.updatedAt) }}</span>
        </button>
        <p class="rail-empty" v-if="!conversations.length">还没有会话</p>
      </nav>
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
          <button class="icon-btn theme-toggle" :title="dark ? '切换浅色' : '切换暗色'" @click="toggleTheme">{{ dark ? '☀' : '☾' }}</button>
          <button class="quiet-btn small" @click="refresh">刷新</button>
        </div>
      </header>

      <section class="thread" ref="messages">
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
        @update:providerId="setProvider" @update:model="model = $event" @update:reasoningEffort="reasoningEffort = $event"
        @update:search="search = $event" @update:draft="draft = $event" @send="send" @stop="stopGeneration" />
    </main>

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
            <label class="field wide">模型（逗号分隔）<input v-model.trim="providerDraft.model" placeholder="deepseek-chat"></label>
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
    };
  },
  computed: {
    api() { return new Api(this.server, this.token); },
    sending() { return !!(this.activeId && this.streams[this.activeId]); },
    streamPhase() { return (this.activeId && this.streams[this.activeId] && this.streams[this.activeId].phase) || ''; },
    providerOptions() { return this.providers.map((p) => ({ value: p.id, label: p.name })); },
    modelOptions() {
      const provider = this.providers.find((p) => p.id === this.providerId);
      return ((provider && provider.model) || '').split(',').map((m) => m.trim()).filter(Boolean).map((m) => ({ value: m, label: m }));
    },
    reasoningOptions() {
      return [{ value: '', label: '思考：自动' }, { value: 'low', label: '思考：低' }, { value: 'high', label: '思考：高' }, { value: 'max', label: '思考：最高' }];
    },
  },
  watch: {
    providerId() {
      const provider = this.providers.find((p) => p.id === this.providerId);
      const first = ((provider && provider.model) || '').split(',')[0].trim();
      if (first && !this.modelOptions.some((m) => m.value === this.model)) this.model = first;
      if (!this.applyingSettings) this.scheduleSettings();
    },
    model() { if (!this.applyingSettings) this.scheduleSettings(); },
    reasoningEffort() { if (!this.applyingSettings) this.scheduleSettings(); },
    search() { if (!this.applyingSettings) this.scheduleSettings(); },
  },
  async mounted() {
    this.applyTheme();
    await this.waitForFonts();
    if (this.token) await this.refresh();
    this.booting = false;
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
      } catch (e) { this.authError = e.message; } finally { this.busy = false; }
    },
    logout() { localStorage.removeItem(TOKEN); localStorage.removeItem(USER); localStorage.removeItem(SERVER); this.token = ''; this.user = ''; this.active = null; this.conversations = []; },
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
    async openConversation(id) {
      this.activeId = id;
      this.railOpen = false;
      const cached = this.conversationCache[id];
      if (cached && this.streams[id]) {
        this.active = cached;
        this.applySettings(cached);
        return;
      }
      try {
        const data = await this.api.conversation(id);
        this.$set(this.conversationCache, id, data);
        this.active = data;
        this.applySettings(data);
      } catch (e) { this.authError = e.message; }
    },
    setProvider(id) {
      this.providerId = id;
      const provider = this.providers.find((p) => p.id === id);
      const first = ((provider && provider.model) || '').split(',')[0].trim();
      this.model = first;
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
      this.providerDraft = { id: uuid(), name: '', protocol: 'CHAT_COMPLETIONS', baseUrl: '', apiKey: '', model: '', systemPrompt: '' };
      this.providerError = '';
    },
    editProvider(p) { this.providerDraft = { ...p }; this.providerError = ''; },
    async saveProvider() {
      if (!this.providerDraft || this.providerBusy) return;
      const p = this.providerDraft;
      if (!p.name.trim() || !p.baseUrl.trim() || !p.model.trim()) { this.providerError = '名称、Base URL、模型不能为空'; return; }
      this.providerBusy = true; this.providerError = '';
      try {
        await this.api.putProvider({ id: p.id, name: p.name.trim(), protocol: p.protocol, baseUrl: p.baseUrl.trim(), apiKey: p.apiKey || '', model: p.model.trim(), systemPrompt: p.systemPrompt || '', temperature: null, maxTokens: null });
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
      const text = this.active.messages[userIndex].content;
      this.active.messages = this.active.messages.slice(0, userIndex + 1);
      await this.sendText(text, true);
    },
    send() { const text = this.draft.trim(); if (!text || this.sending || !this.providerId) return; this.draft = ''; this.sendText(text, false); },
    async sendText(text, retry) {
      if (!this.active) this.newConversation();
      const conversation = this.active;
      conversation.settings = { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search };
      this.$set(this.conversationCache, conversation.id, conversation);
      const now = Date.now();
      if (!retry) conversation.messages.push({ id: uuid(), role: 'user', content: text, images: [], createdAt: now, status: 'COMPLETE', error: null });
      const assistant = { id: uuid(), role: 'assistant', content: '', reasoning: '', model: this.model, toolCalls: [], images: [], createdAt: now + 1, status: 'COMPLETE', error: null, streaming: true };
      conversation.messages.push(assistant);
      const controller = new AbortController();
      this.$set(this.streams, conversation.id, { assistantId: assistant.id, phase: 'waiting', controller, cancelled: false });
      const stream = this.streams[conversation.id];
      try {
        if (this.level >= 2 && this.agentAvailable) await this.streamDsh(conversation, assistant, text, stream);
        else await this.streamRelay(conversation, assistant, stream);
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
      if (users.length !== 1 || !users[0].content) return;
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
    async streamRelay(conversation, assistant, stream) {
      const settings = conversation.settings || {};
      const history = conversation.messages.filter((m) => m !== assistant && m.content).map((m) => ({ id: m.id, role: m.role, content: m.content, images: [], createdAt: m.createdAt, status: 'COMPLETE', error: null }));
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
            if (event.text) { assistant.content = event.text; stream.phase = 'streaming'; }
            if (event.reasoning) assistant.reasoning = event.reasoning;
            if (event.error) throw new Error(String(event.error).slice(0, 4000));
          }
          if (event.type === 'delta') { assistant.content += event.text || ''; stream.phase = 'streaming'; }
          if (event.type === 'reasoning') { assistant.reasoning += event.text || ''; stream.phase = 'streaming'; }
          if (event.type === 'error') throw new Error((event.error || 'relay failed') + (event.status ? '（' + event.status + '）' : ''));
          if (event.type === 'status' && (event.status === 'failed' || event.status === 'cancelled')) {
            throw new Error(event.error || (event.status === 'cancelled' ? '生成已取消' : '生成失败'));
          }
          if (event.type === 'status' && event.status === 'completed') return;
        }
      }
      if (!assistant.content) throw new Error('连接已中断，未收到模型输出');
    },
    async streamDsh(conversation, assistant, text, stream) {
      const settings = conversation.settings || {};
      const res = await fetch(this.api.base() + '/v1/agent/run', { method: 'POST', headers: { Authorization: 'Bearer ' + this.token, 'Content-Type': 'application/json' }, signal: stream.controller.signal, body: JSON.stringify({ providerId: settings.providerId || this.providerId, model: settings.model || this.model, reasoningEffort: settings.reasoningEffort || this.reasoningEffort, conversationId: conversation.id, text }) });
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
            if (event.chunk.type === 'reasoning-delta') { assistant.reasoning += event.chunk.text || ''; stream.phase = 'streaming'; }
          }
          if (name === 'event' && event.type === 'tool/call') (assistant.toolCalls = assistant.toolCalls || []).push({ callId: String(event.data?.callId || ''), name: String(event.data?.name || 'tool'), status: 'running' });
          if (name === 'done') return;
          if (name === 'error') throw new Error((event.message || 'DSH failed') + (event.stderr ? '\n' + String(event.stderr).slice(-2000) : ''));
        }
      }
    },
    async persist(target) {
      const c = target || this.active;
      if (!c) return;
      const settings = c.settings || { providerId: this.providerId, model: this.model, reasoningEffort: this.reasoningEffort, search: this.search };
      const body = { title: c.title || '新会话', createdAt: c.createdAt, updatedAt: Date.now(), messages: c.messages.map((m) => ({ id: m.id, role: m.role, content: m.content || '', reasoning: m.reasoning || '', model: m.model || '', images: m.images || [], createdAt: m.createdAt, status: m.status || 'COMPLETE', error: m.error || null })), settings };
      try {
        const saved = !c.version
          ? await this.api.createConversation({ id: c.id, ...body })
          : await this.api.updateConversation(c.id, { ...body, baseVersion: c.version });
        this.$set(this.conversationCache, saved.id, saved);
        if (this.activeId === saved.id) this.active = saved;
        await this.refresh();
      } catch (e) { this.authError = e.message; }
    },
    formatShort(ms) { if (!ms) return ''; return new Date(ms).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }); },
  },
};
</script>
