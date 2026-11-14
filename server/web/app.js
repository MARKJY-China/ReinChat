/* global Vue */
(function () {
  'use strict';

  const STORAGE = {
    token: 'reinchat_web_token',
    user: 'reinchat_web_user',
    server: 'reinchat_web_server',
  };

  function read(key, fallback) {
    try { return localStorage.getItem(key) || fallback; } catch (e) { return fallback; }
  }
  function write(key, value) {
    try { localStorage.setItem(key, value); } catch (e) { /* ignore */ }
  }
  function remove(key) {
    try { localStorage.removeItem(key); } catch (e) { /* ignore */ }
  }

  new Vue({
    el: '#app',
    data: function () {
      return {
        token: read(STORAGE.token, ''),
        user: read(STORAGE.user, ''),
        server: read(STORAGE.server, window.location.origin),
        username: '',
        password: '',
        mode: 'login',
        busy: false,
        loading: false,
        error: '',
        conversations: [],
        activeId: '',
        active: null,
        syncState: 'idle',
        syncAbort: null,
        atBottom: true,
        follow: true,
        level: 1,
        pendingRequest: null,
        upgradeReason: '',
        upgradeError: '',
        upgradeBusy: false,
        providers: [],
        providerId: '',
        model: '',
        reasoningEffort: '',
        search: false,
        draft: '',
        sending: false,
        editingId: '',
        jobAssistError: '',
        openMenu: '',
        sidebarOpen: false,
        providerModal: false,
        providerRecords: [],
        providerDraft: null,
        providerBusy: false,
        providerError: '',
      };
    },
    watch: {
      providerId: function () {
        const p = this.providers.find(function (item) { return item.id === this.providerId; }.bind(this));
        const options = p ? (p.model || '').split(',').map(function (m) { return m.trim(); }).filter(Boolean) : [];
        if (options.indexOf(this.model) < 0) this.model = options[0] || '';
      },
    },
    computed: {
      modelOptions: function () {
        const p = this.providers.find(function (item) { return item.id === this.providerId; }.bind(this));
        if (!p) return [];
        return (p.model || '').split(',').map(function (m) { return m.trim(); }).filter(Boolean);
      },
      selectedProviderName: function () {
        const p = this.providers.find(function (item) { return item.id === this.providerId; }.bind(this));
        return p ? p.name : '选择服务商';
      },
      selectedReasoningLabel: function () {
        const option = this.reasoningOptions.find(function (item) { return item.value === this.reasoningEffort; }.bind(this));
        return option ? option.label : '思考：自动';
      },
      reasoningOptions: function () {
        return [
          { value: '', label: '思考：自动' },
          { value: 'low', label: '思考：低' },
          { value: 'high', label: '思考：高' },
          { value: 'max', label: '思考：最高' },
        ];
      },
      syncLabel: function () {
        if (this.syncState === 'live') return '实时同步中';
        if (this.syncState === 'connecting') return '正在同步…';
        if (this.syncState === 'offline') return '同步断开，轮询中';
        return '未连接';
      },
    },
    mounted: function () {
      if (this.token) {
        this.refreshAll();
        this.loadAccount();
        this.loadProviders();
        this.loadProviderRecords();
        this.startSync();
      }
    },
    methods: {
      apiBase: function () {
        return this.server.replace(/\/+$/, '') + '/api';
      },
      api: async function (path, options) {
        const opts = options || {};
        const headers = Object.assign({}, opts.headers || {});
        if (this.token) headers.Authorization = 'Bearer ' + this.token;
        if (opts.body && typeof opts.body !== 'string') {
          headers['Content-Type'] = 'application/json';
          opts.body = JSON.stringify(opts.body);
        }
        const res = await fetch(this.apiBase() + path, Object.assign({}, opts, { headers: headers }));
        if (res.status === 401) {
          this.logout();
          throw new Error('登录已过期');
        }
        const text = await res.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
        if (!res.ok) {
          const message = data && data.error && data.error.message ? data.error.message : ('HTTP ' + res.status);
          throw new Error(message);
        }
        return data;
      },
      submitAuth: async function () {
        if (this.busy) return;
        this.busy = true;
        this.error = '';
        try {
          const path = this.mode === 'login' ? '/v1/auth/login' : '/v1/auth/register';
          const body = { username: this.username, password: this.password };
          const res = await fetch(this.server.replace(/\/+$/, '') + '/api' + path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
          });
          const text = await res.text();
          const data = text ? JSON.parse(text) : null;
          if (!res.ok) throw new Error((data && data.error && data.error.message) || ('HTTP ' + res.status));
          this.token = data.token;
          this.user = data.user.username;
          write(STORAGE.token, this.token);
          write(STORAGE.user, this.user);
          write(STORAGE.server, this.server);
          this.password = '';
          this.level = (data.user && data.user.level) || 1;
          this.refreshAll();
          this.loadAccount();
          this.loadProviders();
          this.loadProviderRecords();
          this.startSync();
        } catch (e) {
          this.error = e.message || '登录失败';
        } finally {
          this.busy = false;
        }
      },
      logout: function () {
        if (this.syncAbort) this.syncAbort.abort();
        this.syncAbort = null;
        this.token = '';
        this.user = '';
        this.conversations = [];
        this.active = null;
        this.activeId = '';
        this.syncState = 'idle';
        this.level = 1;
        this.pendingRequest = null;
        this.providers = [];
        this.providerId = '';
        this.model = '';
        this.reasoningEffort = '';
        this.draft = '';
        this.sending = false;
        this.jobAssistError = '';
        this.openMenu = '';
        this.sidebarOpen = false;
        this.providerModal = false;
        this.providerDraft = null;
        this.providerRecords = [];
        remove(STORAGE.token);
        remove(STORAGE.user);
      },
      refreshAll: async function () {
        if (!this.token) return;
        this.loading = true;
        try {
          const data = await this.api('/v1/conversations');
          this.conversations = (data && data.conversations) || [];
          if (this.active && !this.active.version) return;
          if (this.activeId && !this.sending) {
            const still = this.conversations.some(function (c) { return c.id === this.activeId; }.bind(this));
            if (still) await this.openConversation(this.activeId, true);
            else { this.active = null; this.activeId = ''; }
          }
        } catch (e) {
          this.error = e.message;
        } finally {
          this.loading = false;
        }
      },
      loadAccount: async function () {
        try {
          const data = await this.api('/v1/account');
          if (data && data.user) this.level = data.user.level;
          this.pendingRequest = data && data.pendingRequest || null;
        } catch (e) { /* downgrade UI is non-critical */ }
      },
      submitUpgrade: async function () {
        if (this.upgradeBusy) return;
        this.upgradeBusy = true;
        this.upgradeError = '';
        try {
          await this.api('/v1/account/upgrade-requests', {
            method: 'POST',
            body: { reason: this.upgradeReason },
          });
          this.upgradeReason = '';
          await this.loadAccount();
        } catch (e) {
          this.upgradeError = e.message;
        } finally {
          this.upgradeBusy = false;
        }
      },
      loadProviders: async function () {
        try {
          const data = await this.api('/v1/relay/providers');
          this.providers = (data && data.providers) || [];
          if (!this.providerId && this.providers.length) this.providerId = this.providers[0].id;
          if (this.providerId) {
            const p = this.providers.find(function (item) { return item.id === this.providerId; }.bind(this));
            const options = p ? (p.model || '').split(',').map(function (m) { return m.trim(); }).filter(Boolean) : [];
            if (options.indexOf(this.model) < 0) this.model = options[0] || '';
          }
        } catch (e) { /* no synced provider is a valid state */ }
      },
      loadProviderRecords: async function () {
        try {
          const data = await this.api('/v1/providers');
          this.providerRecords = (data && data.providers) || [];
        } catch (e) { this.providerRecords = []; }
      },
      toggleMenu: function (name) {
        this.openMenu = this.openMenu === name ? '' : name;
      },
      closeMenus: function () {
        this.openMenu = '';
      },
      chooseProvider: function (p) {
        this.providerId = p.id;
        this.openMenu = '';
      },
      chooseModel: function (m) {
        this.model = m;
        this.openMenu = '';
      },
      chooseReasoning: function (value) {
        this.reasoningEffort = value;
        this.openMenu = '';
      },
      newConversation: function () {
        if (this.sending) return;
        const now = Date.now();
        this.active = {
          id: this.uuid(),
          title: '新会话',
          createdAt: now,
          updatedAt: now,
          version: 0,
          messages: [],
        };
        this.activeId = this.active.id;
        this.draft = '';
        this.editingId = '';
        this.follow = true;
        this.openMenu = '';
        this.sidebarOpen = false;
        this.$nextTick(this.scrollToBottom);
      },
      openProviderManager: function () {
        this.openMenu = '';
        this.providerModal = true;
        this.providerDraft = null;
        this.providerError = '';
        this.loadProviderRecords();
      },
      newProvider: function () {
        this.providerDraft = {
          id: this.uuid(),
          name: '',
          protocol: 'CHAT_COMPLETIONS',
          baseUrl: '',
          apiKey: '',
          model: '',
          systemPrompt: '',
        };
        this.providerError = '';
      },
      editProvider: function (p) {
        this.providerDraft = Object.assign({}, p);
        this.providerError = '';
      },
      saveProviderDraft: async function () {
        const p = this.providerDraft;
        if (!p || this.providerBusy) return;
        this.providerError = '';
        if (!p.name.trim() || !p.baseUrl.trim() || !p.model.trim()) {
          this.providerError = '名称、Base URL、模型不能为空';
          return;
        }
        this.providerBusy = true;
        try {
          await this.api('/v1/providers/' + encodeURIComponent(p.id), {
            method: 'PUT',
            body: {
              id: p.id,
              name: p.name.trim(),
              protocol: p.protocol || 'CHAT_COMPLETIONS',
              baseUrl: p.baseUrl.trim(),
              apiKey: p.apiKey || '',
              model: p.model.trim(),
              systemPrompt: p.systemPrompt || '',
              temperature: null,
              maxTokens: null,
            },
          });
          this.providerDraft = null;
          await this.loadProviderRecords();
          await this.loadProviders();
        } catch (e) {
          this.providerError = e.message;
        } finally {
          this.providerBusy = false;
        }
      },
      deleteProviderRecord: async function (p) {
        if (!window.confirm('删除服务商「' + p.name + '」？')) return;
        try {
          await this.api('/v1/providers/' + encodeURIComponent(p.id), { method: 'DELETE' });
          if (this.providerId === p.id) this.providerId = '';
          await this.loadProviderRecords();
          await this.loadProviders();
        } catch (e) {
          this.providerError = e.message;
        }
      },
      uuid: function () {
        if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
        return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
          const r = Math.random() * 16 | 0;
          const v = c === 'x' ? r : (r & 0x3 | 0x8);
          return v.toString(16);
        });
      },
      sendMessage: async function () {
        const text = this.draft.trim();
        if (!text || this.sending || this.level < 2 || !this.providerId) return;
        this.jobAssistError = '';
        this.sending = true;
        this.draft = '';
        if (!this.active) {
          this.active = {
            id: this.uuid(),
            title: text.slice(0, 40),
            createdAt: Date.now(),
            updatedAt: Date.now(),
            version: 0,
            messages: [],
          };
          this.activeId = this.active.id;
        }
        if (this.editingId) {
          const editIndex = this.active.messages.findIndex(function (m) { return m.id === this.editingId; }.bind(this));
          if (editIndex >= 0) this.active.messages = this.active.messages.slice(0, editIndex);
          this.editingId = '';
        }
        const now = Date.now();
        const userMessage = {
          id: this.uuid(), role: 'user', content: text, images: [],
          createdAt: now, status: 'COMPLETE', error: null,
        };
        const assistant = {
          id: this.uuid(), role: 'assistant', content: '', reasoning: '', images: [],
          createdAt: now + 1, status: 'COMPLETE', error: null,
        };
        this.active.messages.push(userMessage, assistant);
        if (!this.active.title || this.active.title === '新会话') {
          this.active.title = text.slice(0, 40);
        }
        this.active.updatedAt = now;
        this.follow = true;
        this.$nextTick(this.scrollToBottom);
        try {
          const history = this.active.messages
            .filter(function (m) { return m !== assistant && (m.content || '').trim(); })
            .map(function (m) {
              return {
                id: m.id, role: m.role, content: m.content, images: [],
                createdAt: m.createdAt, status: 'COMPLETE', error: null,
              };
            });
          const job = await this.api('/v1/relay/jobs', {
            method: 'POST',
            body: {
              providerId: this.providerId,
              conversationId: this.active.id,
              model: this.model,
              reasoningEffort: this.reasoningEffort,
              search: this.search,
              messages: history,
            },
          });
          await this.streamRelay(job.id, assistant);
          await this.persistConversation();
        } catch (e) {
          assistant.error = e.message || '生成失败';
          assistant.status = 'ERROR';
          this.jobAssistError = assistant.error;
          try { await this.persistConversation(); } catch (ignored) { /* keep local */ }
        } finally {
          this.sending = false;
        }
      },
      beginEdit: function (message) {
        if (this.sending) return;
        this.editingId = message.id;
        this.draft = message.content;
        this.$nextTick(function () {
          if (this.$refs.draft) this.$refs.draft.focus();
        }.bind(this));
      },
      cancelEdit: function () {
        this.editingId = '';
        this.draft = '';
      },
      retryMessage: async function (index) {
        if (this.sending || !this.active) return;
        const target = this.active.messages[index];
        if (!target || target.role !== 'assistant') return;
        let userIndex = -1;
        for (let i = index - 1; i >= 0; i--) {
          if (this.active.messages[i].role === 'user') { userIndex = i; break; }
        }
        if (userIndex < 0) return;
        this.active.messages = this.active.messages.slice(0, userIndex + 1);
        const assistant = {
          id: this.uuid(), role: 'assistant', content: '', reasoning: '', images: [],
          createdAt: Date.now(), status: 'COMPLETE', error: null,
        };
        this.active.messages.push(assistant);
        this.sending = true;
        this.follow = true;
        this.$nextTick(this.scrollToBottom);
        const history = this.active.messages
          .filter(function (m) { return m !== assistant && (m.content || '').trim(); })
          .map(function (m) {
            return {
              id: m.id, role: m.role, content: m.content, images: [],
              createdAt: m.createdAt, status: 'COMPLETE', error: null,
            };
          });
        try {
          const job = await this.api('/v1/relay/jobs', {
            method: 'POST',
            body: {
              providerId: this.providerId,
              conversationId: this.active.id,
              model: this.model,
              reasoningEffort: this.reasoningEffort,
              search: this.search,
              messages: history,
            },
          });
          await this.streamRelay(job.id, assistant);
          await this.persistConversation();
        } catch (e) {
          assistant.error = e.message || '生成失败';
          assistant.status = 'ERROR';
          this.jobAssistError = assistant.error;
          try { await this.persistConversation(); } catch (ignored) { /* keep local */ }
        } finally {
          this.sending = false;
        }
      },
      rewindMessage: async function (index) {
        if (this.sending || !this.active || index >= this.active.messages.length - 1) return;
        this.active.messages = this.active.messages.slice(0, index + 1);
        this.active.updatedAt = Date.now();
        try {
          await this.persistConversation();
        } catch (e) {
          this.jobAssistError = e.message;
        }
      },
      streamRelay: function (jobId, assistant) {
        return new Promise(async (resolve, reject) => {
          let finalStatus = '';
          try {
            const res = await fetch(this.apiBase() + '/v1/relay/jobs/' + encodeURIComponent(jobId) + '/events', {
              headers: { Authorization: 'Bearer ' + this.token, Accept: 'text/event-stream' },
            });
            if (!res.ok || !res.body) throw new Error('relay HTTP ' + res.status);
            const reader = res.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            while (true) {
              const part = await reader.read();
              if (part.done) break;
              buffer += decoder.decode(part.value, { stream: true });
              let cut;
              while ((cut = buffer.indexOf('\n\n')) >= 0) {
                const block = buffer.slice(0, cut);
                buffer = buffer.slice(cut + 2);
                const dataLines = block.split('\n')
                  .filter(function (line) { return line.indexOf('data:') === 0; })
                  .map(function (line) { return line.slice(5).trim(); });
                if (!dataLines.length) continue;
                let event;
                try { event = JSON.parse(dataLines.join('\n')); } catch (e) { continue; }
                if (event.type === 'snapshot') {
                  assistant.content = event.text || '';
                  assistant.reasoning = event.reasoning || '';
                  finalStatus = event.status || '';
                  if (this.follow) this.$nextTick(this.scrollToBottom);
                } else if (event.type === 'delta') {
                  assistant.content = (assistant.content || '') + (event.text || '');
                  if (this.follow) this.$nextTick(this.scrollToBottom);
                } else if (event.type === 'reasoning') {
                  assistant.reasoning = (assistant.reasoning || '') + (event.text || '');
                } else if (event.type === 'error') {
                  assistant.error = event.error || '生成失败';
                  finalStatus = event.status || 'failed';
                } else if (event.type === 'status') {
                  finalStatus = event.status || '';
                }
                if (finalStatus === 'completed') { resolve(); return; }
                if (finalStatus === 'failed' || finalStatus === 'cancelled') {
                  reject(new Error(assistant.error || '生成中断'));
                  return;
                }
              }
            }
            if (finalStatus === 'completed') resolve();
            else reject(new Error(assistant.error || '连接结束'));
          } catch (e) {
            reject(e);
          }
        });
      },
      persistConversation: async function () {
        if (!this.active) return;
        const now = Date.now();
        const convo = {
          id: this.active.id,
          title: this.active.title || '新会话',
          createdAt: this.active.createdAt || now,
          updatedAt: now,
          messages: this.active.messages.map(function (m) {
            return {
              id: m.id, role: m.role, content: m.content || '', images: m.images || [],
              createdAt: m.createdAt, status: m.status || 'COMPLETE', error: m.error || null,
            };
          }),
        };
        let saved;
        if (!this.active.version) {
          saved = await this.api('/v1/conversations', { method: 'POST', body: convo });
        } else {
          saved = await this.api('/v1/conversations/' + encodeURIComponent(this.active.id), {
            method: 'PUT',
            body: Object.assign({}, convo, { baseVersion: this.active.version }),
          });
        }
        if (this.activeId === saved.id) this.active = Object.assign({}, saved, {
          messages: saved.messages || this.active.messages,
        });
        await this.refreshAll();
      },
      openConversation: async function (id, silent) {
        if (!silent) this.activeId = id;
        try {
          const data = await this.api('/v1/conversations/' + encodeURIComponent(id));
          if (this.activeId === id) {
            this.active = data;
            this.$nextTick(this.scrollToBottom);
          }
        } catch (e) {
          if (!silent) this.error = e.message;
        }
      },
      startSync: async function () {
        if (!this.token) return;
        if (this.syncAbort) this.syncAbort.abort();
        const controller = new AbortController();
        this.syncAbort = controller;
        this.syncState = 'connecting';
        try {
          const res = await fetch(this.apiBase() + '/v1/events', {
            headers: { Authorization: 'Bearer ' + this.token, Accept: 'text/event-stream' },
            signal: controller.signal,
          });
          if (!res.ok || !res.body) throw new Error('SSE HTTP ' + res.status);
          this.syncState = 'live';
          const reader = res.body.getReader();
          const decoder = new TextDecoder();
          let buffer = '';
          while (true) {
            const part = await reader.read();
            if (part.done) break;
            buffer += decoder.decode(part.value, { stream: true });
            let cut;
            while ((cut = buffer.indexOf('\n\n')) >= 0) {
              const block = buffer.slice(0, cut);
              buffer = buffer.slice(cut + 2);
              const dataLines = block.split('\n')
                .filter(function (line) { return line.indexOf('data:') === 0; })
                .map(function (line) { return line.slice(5).trim(); });
              if (!dataLines.length) continue;
              let event;
              try { event = JSON.parse(dataLines.join('\n')); } catch (e) { continue; }
              this.handleSync(event);
            }
          }
          if (!controller.signal.aborted) this.syncOffline();
        } catch (e) {
          if (!controller.signal.aborted) this.syncOffline();
        }
      },
      syncOffline: function () {
        this.syncState = 'offline';
        setTimeout(function () {
          if (this.token && this.syncState !== 'live') this.startSync();
        }.bind(this), 5000);
        this.refreshAll();
      },
      handleSync: function (event) {
        if (!event || !event.type) return;
        if (event.type === 'account.updated') {
          this.loadAccount();
          return;
        }
        if (event.type === 'conversation.deleted') {
          if (this.activeId === event.conversationId) {
            this.activeId = '';
            this.active = null;
          }
          this.refreshAll();
          return;
        }
        if (event.type === 'conversation.updated' || event.type === 'conversation.created') {
          this.refreshAll();
          if (this.activeId === event.conversationId && !this.sending) {
            this.openConversation(event.conversationId, true);
          }
        }
      },
      escapeHtml: function (text) {
        return String(text == null ? '' : text)
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')
          .replace(/"/g, '&quot;')
          .replace(/'/g, '&#39;');
      },
      inlineMarkdown: function (text) {
        let value = this.escapeHtml(text);
        const codes = [];
        value = value.replace(/`([^`]+)`/g, function (match, code) {
          codes.push('<code>' + code + '</code>');
          return '\u0000' + (codes.length - 1) + '\u0000';
        });
        value = value.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, function (match, label, url) {
          return '<a href="' + url.replace(/"/g, '&quot;') + '" target="_blank" rel="noopener noreferrer">' + label + '</a>';
        });
        value = value.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
        value = value.replace(/__([^_]+)__/g, '<strong>$1</strong>');
        value = value.replace(/~~([^~]+)~~/g, '<del>$1</del>');
        value = value.replace(/(^|[^*])\*([^*\n]+)\*/g, '$1<em>$2</em>');
        value = value.replace(/\u0000(\d+)\u0000/g, function (match, index) {
          return codes[Number(index)] || '';
        });
        return value;
      },
      renderMarkdown: function (raw) {
        const source = this.cleanText(raw).replace(/\r\n/g, '\n').replace(/\r/g, '\n');
        if (!source.trim()) return '';
        const lines = source.split('\n');
        const out = [];
        let paragraph = [];
        const flushParagraph = function () {
          if (paragraph.length) {
            out.push('<p>' + paragraph.join('<br>') + '</p>');
            paragraph = [];
          }
        };
        const isBlockStart = function (line, index) {
          if (/^\s*```/.test(line)) return true;
          if (/^\s{0,3}#{1,6}\s+/.test(line)) return true;
          if (/^\s{0,3}(?:[-*+]|\d+[.)])\s+/.test(line)) return true;
          if (/^\s{0,3}>\s?/.test(line)) return true;
          if (/^\s{0,3}(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) return true;
          if (line.indexOf('|') >= 0 && index + 1 < lines.length &&
              /^\s*\|?\s*:?-{2,}/.test(lines[index + 1])) return true;
          return false;
        };
        let i = 0;
        while (i < lines.length) {
          const line = lines[i];
          if (!line.trim()) { flushParagraph(); i++; continue; }

          const fence = line.match(/^\s*```(.*)$/);
          if (fence) {
            flushParagraph();
            const language = this.escapeHtml(fence[1].trim());
            i++;
            const code = [];
            while (i < lines.length && !/^\s*```\s*$/.test(lines[i])) {
              code.push(lines[i]); i++;
            }
            if (i < lines.length) i++;
            out.push('<pre><code' + (language ? ' class="language-' + language + '"' : '') + '>' +
              this.escapeHtml(code.join('\n')) + '</code></pre>');
            continue;
          }

          const heading = line.match(/^\s{0,3}(#{1,6})\s+(.*)$/);
          if (heading) {
            flushParagraph();
            const level = heading[1].length;
            out.push('<h' + level + '>' + this.inlineMarkdown(heading[2].trim()) + '</h' + level + '>');
            i++; continue;
          }

          if (/^\s{0,3}(?:-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
            flushParagraph(); out.push('<hr>'); i++; continue;
          }

          if (/^\s{0,3}>\s?/.test(line)) {
            flushParagraph();
            const quote = [];
            while (i < lines.length && /^\s{0,3}>\s?/.test(lines[i])) {
              quote.push(this.inlineMarkdown(lines[i].replace(/^\s{0,3}>\s?/, '')));
              i++;
            }
            out.push('<blockquote>' + quote.join('<br>') + '</blockquote>');
            continue;
          }

          const listMatch = line.match(/^\s{0,3}([-*+]|\d+[.)])\s+(.*)$/);
          if (listMatch) {
            flushParagraph();
            const ordered = /^\d/.test(listMatch[1]);
            const start = ordered ? parseInt(listMatch[1], 10) : 1;
            const items = [];
            while (i < lines.length) {
              const item = lines[i].match(/^\s{0,3}([-*+]|\d+[.)])\s+(.*)$/);
              if (!item) break;
              items.push('<li>' + this.inlineMarkdown(item[2].trim()) + '</li>');
              i++;
            }
            out.push(ordered
              ? '<ol start="' + (isNaN(start) ? 1 : start) + '">' + items.join('') + '</ol>'
              : '<ul>' + items.join('') + '</ul>');
            continue;
          }

          if (line.indexOf('|') >= 0 && i + 1 < lines.length &&
              /^\s*\|?\s*:?-{2,}/.test(lines[i + 1])) {
            const split = function (row) {
              let value = row.trim();
              if (value.charAt(0) === '|') value = value.slice(1);
              if (value.charAt(value.length - 1) === '|') value = value.slice(0, -1);
              return value.split('|').map(function (cell) { return cell.trim(); });
            };
            const header = split(line);
            const align = split(lines[i + 1]).map(function (cell) {
              if (/^:.*:$/.test(cell)) return 'center';
              if (/:$/.test(cell)) return 'right';
              return 'left';
            });
            i += 2;
            const rows = [];
            while (i < lines.length && lines[i].indexOf('|') >= 0 && lines[i].trim()) {
              rows.push(split(lines[i])); i++;
            }
            out.push('<div class="md-table-wrap"><table><thead><tr>' +
              header.map(function (cell, index) {
                return '<th style="text-align:' + (align[index] || 'left') + '">' + this.inlineMarkdown(cell) + '</th>';
              }.bind(this)).join('') + '</tr></thead><tbody>' +
              rows.map(function (row) {
                return '<tr>' + header.map(function (cell, index) {
                  return '<td style="text-align:' + (align[index] || 'left') + '">' + this.inlineMarkdown(row[index] || '') + '</td>';
                }.bind(this)).join('') + '</tr>';
              }.bind(this)).join('') + '</tbody></table></div>');
            continue;
          }

          paragraph.push(this.inlineMarkdown(line.trim()));
          i++;
          if (i < lines.length && isBlockStart(lines[i], i)) flushParagraph();
        }
        flushParagraph();
        return out.join('');
      },
      formatShort: function (ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const now = new Date();
        if (d.toDateString() === now.toDateString()) {
          return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
        }
        return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
      },
      cleanText: function (text) {
        if (!text) return '';
        return String(text)
          .replace(/<[^>]*DSML[^>]*calls[^>]*>[\s\S]*?<\/[^>]*DSML[^>]*calls[^>]*>/gi, '')
          .replace(/<[^>]*DSML[^>]*>/gi, '')
          .trim();
      },
      formatTime: function (ms) {
        if (!ms) return '';
        const d = new Date(ms);
        const now = new Date();
        if (d.toDateString() === now.toDateString()) {
          return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
        }
        return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' }) + ' ' +
          d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
      },
      scrollToBottom: function () {
        const el = this.$refs.messages;
        if (el) {
          el.scrollTop = el.scrollHeight;
          this.atBottom = true;
          this.follow = true;
        }
      },
      onMessagesScroll: function () {
        const el = this.$refs.messages;
        if (!el) return;
        const bottom = el.scrollHeight - el.scrollTop - el.clientHeight < 48;
        this.atBottom = bottom;
        this.follow = bottom;
      },
      jumpToBottom: function () {
        this.follow = true;
        this.scrollToBottom();
      },
    },
  });
})();
