<template>
  <div class="login-page">
    <div class="login-theme"><ThemeToggle /></div>
    <div class="login-box">
      <h1>智汇学场</h1>
      <p class="subtitle">一体化智能学习平台 · 考试 / 竞赛 / 悬赏 / 自学</p>
      <div class="login-tabs">
        <button :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>
      <el-form :model="form" label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password />
        </el-form-item>
        <el-form-item label="角色" v-if="mode === 'register'">
          <el-radio-group v-model="form.role" size="large" style="width: 100%;">
            <el-radio-button value="student" style="flex: 1;">学生</el-radio-button>
            <el-radio-button value="teacher" style="flex: 1;">教师</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-button type="primary" size="large" style="width: 100%; margin-top: 8px;" :loading="loading" @click="submit">
          {{ mode === 'login' ? '登录' : '注册并登录' }}
        </el-button>
        <div class="test-accounts">
          <strong>测试账号：</strong><br>
          教师：teacher / 123123<br>
          学生：student1 / 123123
        </div>
        </el-form>
      <div class="login-foot">
        <a @click="router.push('/')">← 返回首页</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import ThemeToggle from '../components/ThemeToggle.vue'

const authStore = useAuthStore()
const router = useRouter()
const mode = ref('login')
const loading = ref(false)
const form = reactive({ username: '', password: '', role: 'student' })

onMounted(() => {
  if (authStore.isLoggedIn) {
    router.push('/portal')
  }
})

async function submit() {
  if (!form.username || !form.password) { ElMessage.warning('请填写完整'); return }
  loading.value = true
  try {
    if (mode.value === 'login') await authStore.login(form.username, form.password)
    else await authStore.register(form.username, form.password, form.role)
    ElMessage.success(mode.value === 'login' ? '登录成功' : '注册成功')
    router.push('/portal')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-theme { position: absolute; top: 22px; right: 24px; z-index: 2; }
.login-tabs {
  display: flex; gap: 6px; padding: 5px; margin-bottom: 24px;
  background: var(--panel-2); border-radius: 12px;
}
.login-tabs button {
  flex: 1; padding: 10px 0; border-radius: 9px; background: transparent;
  color: var(--muted); font-weight: 700; transition: all .2s;
}
.login-tabs button.active {
  background: var(--surface); color: var(--el-color-primary-dark-2);
  box-shadow: 0 4px 12px rgba(24,32,64,.10);
}
.test-accounts {
  margin-top: 16px; padding: 12px 14px; border-radius: 10px; font-size: 13px; line-height: 1.7;
  background: var(--el-color-primary-light-9); color: var(--ink-2); border: 1px solid var(--line);
}
.login-foot { text-align: center; margin-top: 18px; }
.login-foot a { color: var(--muted); font-size: 13px; cursor: pointer; transition: color .2s; }
.login-foot a:hover { color: var(--el-color-primary); }
</style>