export class Api {
  constructor(server, token) {
    this.server = (server || window.location.origin).replace(/\/+$/, '');
    this.token = token || '';
  }
  base() { return this.server + '/api'; }
  async request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (this.token) headers.Authorization = 'Bearer ' + this.token;
    let body = options.body;
    if (body && typeof body !== 'string') {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }
    const res = await fetch(this.base() + path, { ...options, body, headers });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { /* ignore */ }
    if (!res.ok) {
      const message = data?.error?.message || ('HTTP ' + res.status);
      const err = new Error(message);
      err.status = res.status;
      err.code = data?.error?.code;
      throw err;
    }
    return data;
  }
  auth(action, username, password) {
    return this.request('/v1/auth/' + action, { method: 'POST', body: { username, password } });
  }
  account() { return this.request('/v1/account'); }
  conversations() { return this.request('/v1/conversations'); }
  conversation(id) { return this.request('/v1/conversations/' + encodeURIComponent(id)); }
  providers() { return this.request('/v1/providers'); }
  relayProviders() { return this.request('/v1/relay/providers'); }
  putProvider(provider) {
    return this.request('/v1/providers/' + encodeURIComponent(provider.id), { method: 'PUT', body: provider });
  }
  deleteProvider(id) {
    return this.request('/v1/providers/' + encodeURIComponent(id), { method: 'DELETE' });
  }
  createConversation(conversation) {
    return this.request('/v1/conversations', { method: 'POST', body: conversation });
  }
  updateConversation(id, body) {
    return this.request('/v1/conversations/' + encodeURIComponent(id), { method: 'PUT', body });
  }
  async uploadFile(file) {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(this.base() + '/v1/files', { method: 'POST', headers: { Authorization: 'Bearer ' + this.token }, body: form });
    const raw = await res.text();
    let data = {};
    try { data = raw ? JSON.parse(raw) : {}; } catch (e) { /* ignore */ }
    if (!res.ok) throw new Error((data.error && data.error.message) || ('上传失败 HTTP ' + res.status));
    return data;
  }
  deleteConversation(id) {
    return this.request('/v1/conversations/' + encodeURIComponent(id), { method: 'DELETE' });
  }
  async renameConversation(id, title) {
    const conversation = await this.conversation(id);
    return this.updateConversation(id, {
      title,
      createdAt: conversation.createdAt,
      updatedAt: Date.now(),
      messages: conversation.messages || [],
      settings: conversation.settings || {},
      baseVersion: conversation.version,
    });
  }
}
