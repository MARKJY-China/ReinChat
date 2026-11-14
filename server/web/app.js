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
        remove(STORAGE.token);
        remove(STORAGE.user);
      },
      refreshAll: async function () {
        if (!this.token) return;
        this.loading = true;
        try {
          const data = await this.api('/v1/conversations');
          this.conversations = (data && data.conversations) || [];
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
