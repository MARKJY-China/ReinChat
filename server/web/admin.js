/* global Vue */
(function () {
  'use strict';
  const KEY = 'reinchat_admin_token';

  new Vue({
    el: '#app',
    data: {
      token: localStorage.getItem(KEY) || '',
      tokenInput: '',
      tickets: [],
      users: [],
      notes: {},
      query: '',
      error: '',
    },
    mounted: function () {
      if (this.token) {
        this.loadTickets('pending');
        this.loadUsers();
      }
    },
    methods: {
      api: async function (path, options) {
        const opts = options || {};
        const res = await fetch('/api/admin' + path, {
          method: opts.method || 'GET',
          headers: Object.assign({
            'X-Admin-Token': this.token,
            'Content-Type': 'application/json',
          }, opts.headers || {}),
          body: opts.body ? JSON.stringify(opts.body) : undefined,
        });
        if (res.status === 401) { this.logout(); throw new Error('管理员 token 无效'); }
        const text = await res.text();
        let data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { data = null; }
        if (!res.ok) throw new Error((data && data.error && data.error.message) || ('HTTP ' + res.status));
        return data;
      },
      login: function () {
        this.token = this.tokenInput.trim();
        localStorage.setItem(KEY, this.token);
        this.error = '';
        this.loadTickets('pending');
        this.loadUsers();
      },
      logout: function () {
        this.token = '';
        localStorage.removeItem(KEY);
        this.tickets = [];
        this.users = [];
      },
      loadTickets: async function (status) {
        try {
          const q = status ? ('?status=' + encodeURIComponent(status)) : '';
          const data = await this.api('/upgrade-requests' + q);
          this.tickets = (data && data.requests) || [];
        } catch (e) { this.error = e.message; }
      },
      review: async function (ticket, action) {
        try {
          const body = action === 'reject'
            ? { reason: (this.notes[ticket.id] || '').trim() || '不符合升级条件', note: this.notes[ticket.id] || '' }
            : { note: this.notes[ticket.id] || '' };
          await this.api('/upgrade-requests/' + encodeURIComponent(ticket.id) + '/' + action, {
            method: 'POST', body: body,
          });
          await this.loadTickets('pending');
          await this.loadUsers();
        } catch (e) { this.error = e.message; }
      },
      loadUsers: async function () {
        try {
          const data = await this.api('/users?q=' + encodeURIComponent(this.query || ''));
          this.users = (data && data.users) || [];
        } catch (e) { this.error = e.message; }
      },
      setLevel: async function (user) {
        try {
          await this.api('/users/' + user.id + '/level', { method: 'POST', body: { level: user.level } });
        } catch (e) {
          this.error = e.message;
          this.loadUsers();
        }
      },
      formatTime: function (ms) {
        if (!ms) return '';
        return new Date(ms).toLocaleString('zh-CN', { hour12: false });
      },
    },
  });
})();
