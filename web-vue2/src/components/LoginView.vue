<template>
  <div class="auth">
    <header class="auth-brand">
      <span class="wordmark"><span class="mark">R</span>ReinChat</span>
      <span class="auth-version">WEB</span>
    </header>

    <main class="auth-main">
      <p class="eyebrow">A quiet place to think</p>
      <h1>安静地，<br>和 AI 对话。</h1>
      <p class="lede">一个克制的对话空间。支持云端同步，也支持你自己的模型。</p>

      <form class="auth-form" @submit.prevent="submit">
        <label class="field">
          <span>服务器</span>
          <input v-model.trim="server" placeholder="https://reinchat.markjy.com" autocomplete="url">
        </label>
        <div class="field-row">
          <label class="field">
            <span>用户名</span>
            <input v-model.trim="username" placeholder="用户名" autocomplete="username">
          </label>
          <label class="field">
            <span>密码</span>
            <input v-model="password" type="password" placeholder="密码" autocomplete="current-password">
          </label>
        </div>
        <p class="auth-error" v-if="error">{{ error }}</p>
        <button class="primary-btn" type="submit" :disabled="busy">
          {{ busy ? '连接中…' : (mode === 'register' ? '创建账号' : '继续') }}
          <span class="arrow">→</span>
        </button>
        <button class="quiet-btn" type="button" @click="mode = mode === 'login' ? 'register' : 'login'">
          {{ mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}
        </button>
      </form>
    </main>

    <footer class="auth-foot">
      <span>ReinChat</span>
      <span>服务端中转 · 不保存明文密钥</span>
    </footer>
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
      if (this.busy) return;
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
