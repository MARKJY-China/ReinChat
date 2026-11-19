import Vue from 'vue';
import App from './App.vue';
import './style.css';

Vue.config.productionTip = false;
const savedTheme = localStorage.getItem('reinchat_theme');
if (savedTheme === 'dark' || (!savedTheme && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
  document.documentElement.classList.add('dark');
}

new Vue({
  render: (h) => h(App),
}).$mount('#app');
