import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authAPI } from '../api/endpoints'

export const useAuthStore = defineStore('auth', () => {
  // 读取本地凭证：历史版本/异常写入可能留下字符串 "undefined"，统一做防御，避免 JSON.parse 抛错白屏
  const userStr = localStorage.getItem('user')
  let parsedUser = null
  try {
    parsedUser = userStr && userStr !== 'undefined' && userStr !== 'null' ? JSON.parse(userStr) : null
  } catch (e) {
    parsedUser = null
  }
  const storedToken = localStorage.getItem('token')
  const token = ref(storedToken && storedToken !== 'undefined' && storedToken !== 'null' ? storedToken : '')
  if (!token.value || !parsedUser) {
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }
  const currentUser = ref(parsedUser)
  const isLoggedIn = computed(() => !!token.value)

  function persist(tokenVal, userVal) {
    // 写入侧同样防御：绝不把 undefined 写进 localStorage（会存成字符串 "undefined"）
    if (tokenVal && userVal) {
      localStorage.setItem('token', tokenVal)
      localStorage.setItem('user', JSON.stringify(userVal))
    } else {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }

  async function login(username, password) {
    const data = await authAPI.login({ username, password })
    token.value = data.token
    currentUser.value = data.user
    persist(data.token, data.user)
    return data
  }

  async function register(username, password, role) {
    const data = await authAPI.register({ username, password, role })
    token.value = data.token
    currentUser.value = data.user
    persist(data.token, data.user)
    return data
  }

  function logout() {
    token.value = ''
    currentUser.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, currentUser, isLoggedIn, login, register, logout }
})