import { defineStore } from 'pinia'
import { ref, watchEffect } from 'vue'

// 计算初始暗色状态：优先本地存储，其次跟随系统偏好
function initialDark() {
  const stored = localStorage.getItem('theme')
  if (stored) return stored === 'dark'
  return !!(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
}

export const useThemeStore = defineStore('theme', () => {
  const dark = ref(initialDark())

  // 应用主题：切换 <html> 的 dark 类，并同步 color-scheme（影响原生控件/滚动条）
  watchEffect(() => {
    const el = document.documentElement
    el.classList.toggle('dark', dark.value)
    el.style.colorScheme = dark.value ? 'dark' : 'light'
  })

  function toggle() {
    dark.value = !dark.value
    localStorage.setItem('theme', dark.value ? 'dark' : 'light')
  }

  function setDark(val) {
    dark.value = !!val
    localStorage.setItem('theme', dark.value ? 'dark' : 'light')
  }

  return { dark, toggle, setDark }
})
