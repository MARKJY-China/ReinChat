<template>
  <LoginView v-if="!token" :error="authError" :busy="busy" :initial-server="server" @submit="login" />
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="sidebar-head">
        <div class="brand small"><img class="brand-icon" src="/icon-192.png" alt=""><span>ReinChat <small>WEB</small></span></div>
        <button class="icon-btn" title="新建会话" @click="newConversation">＋</button>
      </div>
      <button class="new-chat" @click="newConversation"><span class="new-chat-icon">＋</span><span>新建会话</span><span class="kbd">N</span></button>
      <div class="section-label"><span>会话</span><span>{{ conversations.length }}</span></div>
      <div class="conv-list">
        <button v-for="c in conversations" :key="c.id" class="conv" :class="{ active: c.id === activeId }" @click="openConversation(c.id)">
          <span class="conv-dot"></span>
          <span class="conv-main"><span class="conv-title">{{ c.title || '未命名会话' }}</span><span class="conv-preview">{{ c.preview }}</span></span>
          <span class="conv-meta">{{ formatShort(c.updatedAt) }}</span>
        </button>
        <p class="empty-small" v-if="!conversations.length">还没有云端会话</p>
      </div>
      <div class="sidebar-foot">
        <div class="account-card">
          <div class="account-top"><span class="avatar">{{ user.slice(0, 1).toUpperCase() }}</span><span class="account-name">{{ user }}<small>LV{{ level }}</small></span><button class="link" @click="logout">退出</button></div>
          <div class="account-links"><button class="link small" @click="openProviderManager">服务商管理</button><a class="link small" href="/admin">管理台</a></div>
        </div>
      </div>
    </aside>
    <main class="chat">
      <header class="chat-head">
        <div class="head-title"><div class="sync-line"><span class="sync live">实时同步中</span></div><h1>{{ active ? (active.title || '新会话') : 'ReinChat' }}</h1></div>
        <div class="head-actions"><button class="ghost head-btn" @click="refresh">刷新</button><button class="primary head-btn" @click="newConversation">新对话</button></div>
      </header>
      <section class="messages" ref="messages">
        <div class="messages-inner">
          <template v-if="active">
            <MessageItem v-for="(m, index) in active.messages" :key="m.id" :message="m" :streaming="sending" @retry="retry(m)" @edit="beginEdit" @rewind="rewind(index)" />
            <div class="empty-chat" v-if="!active.messages.length"><img src="/icon-192.png" alt=""><h2>开始新的对话</h2><p>在下方输入问题，ReinChat 会实时同步到你的所有设备。</p></div>
          </template>
          <div class="empty-chat" v-else><img src="/icon-192.png" alt=""><h2>选择一个会话</h2><p>或点击左侧「新建会话」开始。</p></div>
        </div>
      </section>
      <ComposerBar
        :provider-id="providerId" :model="model" :reasoning-effort="reasoningEffort" :search="search" :draft="draft" :sending="sending" :level="level"
        :provider-options="providerOptions" :model-options="modelOptions" :reasoning-options="reasoningOptions"
        @update:providerId="setProvider" @update:model="model = $event" @update:reasoningEffort="reasoningEffort = $event"
        @update:search="search = $event" @update:draft="draft = $event" @send="send" />
    </main>
    <div v-if="providerModal" class="modal-mask" @click.self="providerModal = false">
      <div class="modal-card">
        <div class="modal-head">
          <div><h2>服务商</h2><p class="muted">配置用于服务端 Agent 的模型服务</p></div>
          <button class="icon-btn" @click="providerModal = false">×</button>
        </div>
        <template v-if="!providerDraft">
          <div class="provider-list">
            <div v-for="p in providerRecords" :key="p.id" class="provider-row">
              <div class="provider-avatar">{{ p.name.slice(0, 1).toUpperCase() }}</div>
              <div class="provider-info"><strong>{{ p.name }}</strong><span>{{ p.baseUrl }}</span><small>{{ p.model }} · {{ p.protocol }}</small></div>
              <button class="ghost small" @click="editProvider(p)">编辑</button>
              <button class="ghost small danger-text" @click="deleteProvider(p)">删除</button>
            </div>
            <p v-if="!providerRecords.length" class="empty-small">暂无服务商</p>
          </div>
          <div class="modal-actions"><button class="primary" @click="newProvider">＋ 添加服务商</button></div>
        </template>
        <template v-else>
          <div class="form-grid">
            <label>名称<input v-model.trim="providerDraft.name" placeholder="DeepSeek"></label>
            <label>协议
              <div class="segmented">
                <button :class="{ active: providerDraft.protocol === 'CHAT_COMPLETIONS' }" @click="providerDraft.protocol = 'CHAT_COMPLETIONS'">Chat Completions</button>
                <button :class="{ active: providerDraft.protocol === 'RESPONSES' }" @click="providerDraft.protocol = 'RESPONSES'">Responses</button>
              </div>
            </label>
            <label>Base URL<input v-model.trim="providerDraft.baseUrl" placeholder="https://api.deepseek.com/v1"></label>
            <label>API Key<input v-model.trim="providerDraft.apiKey" type="password"></label>
            <label class="wide">模型（逗号分隔）<input v-model.trim="providerDraft.model" placeholder="deepseek-chat,deepseek-reasoner"></label>
            <label class="wide">系统提示词<textarea v-model="providerDraft.systemPrompt" rows="3"></textarea></label>
          </div>
          <p class="error" v-if="providerError">{{ providerError }}</p>
          <div class="modal-actions"><button class="ghost" @click="providerDraft = null">返回</button><button class="primary" :disabled="providerBusy" @click="saveProvider">{{ providerBusy ? '保存中…' : '保存' }}</button></div>
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
      user: localStorage.getItem(USER) || '',
      server: localStorage.getItem(SERVER) || window.location.origin,
      authError: '', busy: false,
      conversations: [], active: null, activeId: '',
      providers: [], providerRecords: [], providerId: '', model: '', reasoningEffort: 'high', search: true,
      draft: '', level: 1, agentAvailable: false,
      conversationCache: {}, streams: {}, applyingSettings: false, settingsTimer: null,
      providerModal: false, providerDraft: null, providerBusy: false, providerError: '',
    };
  },
  computed: {
    api() { return new Api(this.server, this.token); },
    sending() { return !!(this.activeId && this.streams[this.activeId]); },
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
  mounted() { if (this.token) this.refresh(); },
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
      try { this.active = await this.api.conversation(id); } catch (e) { this.authError = e.message; }
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
    newConversation() {
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
      this.$set(this.streams, conversation.id, { assistantId: assistant.id });
      try {
        if (this.level >= 2 && this.agentAvailable) await this.streamDsh(conversation, assistant, text);
        else await this.streamRelay(conversation, assistant);
        await this.persist(conversation);
      } catch (e) { assistant.error = e.message; assistant.status = 'ERROR'; }
      finally { assistant.streaming = false; this.$delete(this.streams, conversation.id); }
    },
    async streamRelay(conversation, assistant) {
      const settings = conversation.settings || {};
      const history = conversation.messages.filter((m) => m !== assistant && m.content).map((m) => ({ id: m.id, role: m.role, content: m.content, images: [], createdAt: m.createdAt, status: 'COMPLETE', error: null }));
      const job = await this.api.request('/v1/relay/jobs', { method: 'POST', body: { providerId: settings.providerId || this.providerId, conversationId: conversation.id, model: settings.model || this.model, reasoningEffort: settings.reasoningEffort || this.reasoningEffort, search: settings.search !== false, messages: history } });
      const res = await fetch(this.api.base() + '/v1/relay/jobs/' + encodeURIComponent(job.id) + '/events', { headers: { Authorization: 'Bearer ' + this.token } });
      if (!res.ok) throw new Error('relay HTTP ' + res.status);
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
          if (event.type === 'delta') assistant.content += event.text || '';
          if (event.type === 'reasoning') assistant.reasoning += event.text || '';
          if (event.type === 'error') throw new Error(event.error || 'relay failed');
          if (event.type === 'status' && (event.status === 'completed' || event.status === 'failed')) return;
        }
      }
    },
    async streamDsh(conversation, assistant, text) {
      const settings = conversation.settings || {};
      const res = await fetch(this.api.base() + '/v1/agent/run', { method: 'POST', headers: { Authorization: 'Bearer ' + this.token, 'Content-Type': 'application/json' }, body: JSON.stringify({ providerId: settings.providerId || this.providerId, model: settings.model || this.model, reasoningEffort: settings.reasoningEffort || this.reasoningEffort, conversationId: conversation.id, text }) });
      if (!res.ok) throw new Error('DSH HTTP ' + res.status);
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
            if (event.chunk.type === 'text-delta') assistant.content += event.chunk.text || '';
            if (event.chunk.type === 'reasoning-delta') assistant.reasoning += event.chunk.text || '';
          }
          if (name === 'event' && event.type === 'tool/call') (assistant.toolCalls = assistant.toolCalls || []).push({ callId: String(event.data?.callId || ''), name: String(event.data?.name || 'tool'), status: 'running' });
          if (name === 'done') return;
          if (name === 'error') throw new Error(event.message || 'DSH failed');
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
