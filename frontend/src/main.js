import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import App from './App.vue'
import router from './router'
import './style.css'

// 挂载前预置主题类，避免首屏闪烁（FOUC）；与 stores/theme.js 的初始化逻辑保持一致
const _stored = localStorage.getItem('theme')
const _dark = _stored ? _stored === 'dark'
  : !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
if (_dark) document.documentElement.classList.add('dark')

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
