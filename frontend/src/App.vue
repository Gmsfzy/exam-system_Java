<template>
  <div id="app">
    <!-- 未登录：公开页（首页 / 登录 / 注册）全屏渲染，无外壳 -->
    <router-view v-if="!auth.isLoggedIn" />

    <!-- 已登录：统一顶栏 + （模块内）专属侧边栏 -->
    <el-container v-else class="app-shell" direction="vertical">
      <!-- 顶栏 -->
      <header class="app-topbar">
        <div class="tb-left">
          <div class="tb-brand" @click="go('/portal')">
            <span class="tb-logo">智</span>
            <span class="tb-name">智汇学场</span>
          </div>
          <el-divider direction="vertical" />
          <el-dropdown trigger="click" @command="enterModule">
            <span class="tb-module">
              <span class="tb-module-icon">{{ currentModule ? MODULES[currentModule].icon : '🏠' }}</span>
              <span>{{ currentModule ? MODULES[currentModule].title : '门户中枢' }}</span>
              <el-icon class="tb-caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="__portal">🏠 返回门户中枢</el-dropdown-item>
                <el-dropdown-item
                  v-for="m in moduleList" :key="m.key" :command="m.key"
                  :divided="m.key === 'learning'">
                  {{ m.icon }} {{ m.title }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <div class="tb-right">
          <ThemeToggle />
          <span class="tb-user">
            <strong>{{ auth.currentUser?.username || '用户' }}</strong>
            <el-tag size="small" :type="auth.currentUser?.role === 'teacher' ? 'warning' : 'success'" style="margin-left:8px;">
              {{ auth.currentUser?.role === 'teacher' ? '教师' : '学生' }}
            </el-tag>
          </span>
          <el-button size="small" @click="go('/portal')">门户</el-button>
          <el-button size="small" type="danger" plain @click="logout">退出</el-button>
        </div>
      </header>

      <el-container class="app-body">
        <!-- 侧边栏：仅在进入某个模块时出现，且只显示该模块的功能 -->
        <el-aside v-if="currentModule" width="210px" class="app-aside">
          <el-menu :default-active="activeMenu" router class="side-menu">
            <div class="aside-title">{{ MODULES[currentModule].icon }} {{ MODULES[currentModule].title }}</div>
            <el-menu-item v-for="item in menuItems" :key="item.to" :index="item.to">
              <span class="mi-dot"></span>{{ item.label }}
            </el-menu-item>
          </el-menu>
          <div class="aside-back" @click="go('/portal')">‹ 切换其他模块</div>
        </el-aside>

        <el-main class="app-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { ArrowDown } from '@element-plus/icons-vue'
import ThemeToggle from './components/ThemeToggle.vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// 四大模块：入口（按角色）+ 各自专属侧边栏菜单
const MODULES = {
  exam: {
    title: '在线考试', icon: '📝',
    entry: (role) => (role === 'teacher' ? '/dashboard' : '/my-exams'),
    menu: [
      { to: '/dashboard', label: '数据概览', roles: ['teacher'] },
      { to: '/exams', label: '考试管理', roles: ['teacher'] },
      { to: '/questions', label: '题库管理', roles: ['teacher'] },
      { to: '/majors', label: '学科分类', roles: ['teacher'] },
      { to: '/grading', label: '人工阅卷', roles: ['teacher'] },
      { to: '/results', label: '成绩查看', roles: ['teacher'] },
      { to: '/my-exams', label: '我的考试', roles: ['student'] },
      { to: '/my-results', label: '我的成绩', roles: ['student'] }
    ]
  },
  learning: {
    title: '自主学习', icon: '📚', entry: '/learning',
    menu: [
      { to: '/learning', label: '学习中心' },
      { to: '/learning/wrong-notebook', label: '错题本' },
      { to: '/learning/practice', label: '自由刷题' },
      { to: '/learning/plans', label: '学习计划' },
      { to: '/learning/report', label: '学习报告' }
    ]
  },
  competition: {
    title: '答题竞赛', icon: '🏆', entry: '/competition',
    menu: [
      { to: '/competition', label: '竞赛首页' },
      { to: '/competition/plaza', label: '限时赛广场' },
      { to: '/competition/manage', label: '竞赛管理', roles: ['teacher'] },
      { to: '/competition/pk', label: '1v1 对战', roles: ['student'] },
      { to: '/rank', label: '段位中心' },
      { to: '/team', label: '战队中心' },
      { to: '/profile/study', label: '学情画像' }
    ]
  },
  bounty: {
    title: '征集悬赏', icon: '💰', entry: '/bounty',
    menu: [
      { to: '/bounty', label: '悬赏广场' },
      { to: '/bounty/publish', label: '发布悬赏' },
      { to: '/bounty/mine', label: '我的悬赏' }
    ]
  }
}

const currentModule = computed(() => route.meta.module)          // 未进入模块（如门户）时为 undefined
const moduleList = computed(() => Object.keys(MODULES).map(k => ({ key: k, ...MODULES[k] })))
const role = computed(() => auth.currentUser?.role)
const menuItems = computed(() => {
  const m = currentModule.value
  if (!m) return []
  return MODULES[m].menu.filter(x => !x.roles || x.roles.includes(role.value))
})
const activeMenu = computed(() => route.path)

function go(to) { router.push(to) }
function enterModule(cmd) {
  if (cmd === '__portal') { router.push('/portal'); return }
  const m = MODULES[cmd]
  if (!m) return
  const target = typeof m.entry === 'function' ? m.entry(role.value) : m.entry
  router.push(target)
}
function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-shell { min-height: 100vh; background: transparent; }

/* 顶栏：玻璃拟态 */
.app-topbar {
  height: 60px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 22px;
  background: var(--topbar-bg); backdrop-filter: saturate(180%) blur(12px);
  border-bottom: 1px solid var(--topbar-bd);
  box-shadow: 0 2px 16px rgba(24,32,64,.05);
  position: sticky; top: 0; z-index: 60;
}
.tb-left { display: flex; align-items: center; }
.tb-brand { display: flex; align-items: center; cursor: pointer; }
.tb-logo {
  width: 34px; height: 34px; border-radius: 10px; color: #fff; font-weight: 800; font-size: 18px;
  background: var(--brand-grad); display: flex; align-items: center; justify-content: center;
  margin-right: 10px; box-shadow: 0 6px 16px rgba(91,107,255,.36);
}
.tb-name {
  font-size: 18px; font-weight: 800; letter-spacing: 1px;
  background: var(--brand-grad); -webkit-background-clip: text; background-clip: text; color: transparent;
}
.tb-module { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 15px; color: var(--ink); padding: 6px 12px; border-radius: var(--radius-pill); transition: background .2s; font-weight: 600; }
.tb-module:hover { background: var(--el-color-primary-light-9); }
.tb-module-icon { font-size: 16px; }
.tb-caret { font-size: 12px; color: var(--muted); }
.tb-right { display: flex; align-items: center; gap: 12px; }
.tb-user { font-size: 14px; color: var(--ink-2); display: flex; align-items: center; }

/* 主体 */
.app-body { height: calc(100vh - 60px); }
.app-aside {
  background: linear-gradient(180deg, #20263f 0%, #2a3050 100%);
  overflow-y: auto; padding: 8px 0 20px;
}
.aside-title { padding: 18px 22px 10px; font-size: 12px; color: #7f8bab; letter-spacing: 1px; font-weight: 700; text-transform: uppercase; }
.side-menu { background: transparent !important; border-right: none !important; }
.side-menu :deep(.el-menu-item) {
  height: 46px; line-height: 46px; margin: 4px 12px; border-radius: 10px;
  color: #c3cbdf !important; background: transparent !important; font-weight: 500;
  display: flex; align-items: center;
}
.side-menu :deep(.el-menu-item:hover) { background: rgba(255,255,255,.06) !important; color: #fff !important; }
.side-menu :deep(.el-menu-item.is-active) {
  background: var(--brand-grad) !important; color: #fff !important; font-weight: 700;
  box-shadow: 0 8px 18px rgba(91,107,255,.4);
}
.mi-dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; opacity: .55; margin-right: 10px; }
.aside-back {
  margin: 18px 16px 0; padding: 11px; text-align: center;
  border: 1px dashed rgba(255,255,255,.22); border-radius: 10px;
  color: #9fb0cf; font-size: 13px; cursor: pointer; transition: all .2s;
}
.aside-back:hover { color: #fff; border-color: var(--el-color-primary); background: rgba(91,107,255,.14); }
.app-main { background: transparent; padding: 24px; overflow-y: auto; }
</style>
