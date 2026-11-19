<template>
  <div class="login-shell">
    <div class="login-card">
      <img class="login-logo" src="/icon-192.png" alt="ReinChat">
      <div class="login-brand">ReinChat <small>WEB</small></div>
      <p class="login-tagline">一个安静、流畅的 AI 对话空间。与 App 实时同步。</p>
      <label>服务器</label>
      <input v-model.trim="server" placeholder="https://reinchat.markjy.com" autocomplete="url">
      <label>用户名</label>
      <input v-model.trim="username" placeholder="输入用户名" autocomplete="username">
      <label>密码</label>
      <input v-model="password" type="password" placeholder="输入密码" autocomplete="current-password" @keyup.enter="submit">
      <p class="error" v-if="error">{{ error }}</p>
      <div class="login-actions">
        <button class="primary" :disabled="busy" @click="submit">{{ busy ? '连接中…' : (mode === 'login' ? '登录' : '创建账号') }}</button>
        <button class="text-btn" @click="mode = mode === 'login' ? 'register' : 'login'">{{ mode === 'login' ? '注册账号' : '已有账号' }}</button>
      </div>
      <div class="login-foot">端到端加密传输 · 服务端中转</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'LoginView',
  props: {
    error: { type: String, default: '' },
    busy: { type: Boolean, default: false },
    initialServer: { type: String, default: '' },
  },
  data() {
    return {
      server: this.initialServer || window.location.origin,
      username: '',
      password: '',
      mode: 'login',
    };
  },
  methods: {
    submit() {
      this.$emit('submit', {
        mode: this.mode,
        server: this.server,
        username: this.username,
        password: this.password,
      });
    },
  },
};
</script>
