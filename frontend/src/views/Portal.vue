<template>
  <div class="portal">
    <!-- 欢迎横幅 -->
    <div class="welcome">
      <div class="welcome-text">
        <h1>{{ greeting }}，{{ auth.currentUser?.username }} 👋</h1>
        <p>欢迎回到智汇学场 · 选择一个模块开始你的学习与教学</p>
      </div>
      <div class="welcome-role">
        <el-tag :type="isTeacher ? 'warning' : 'success'" size="large" effect="dark" round>
          {{ isTeacher ? '教师工作台' : '学生工作台' }}
        </el-tag>
      </div>
    </div>

    <!-- 四大模块 -->
    <h3 class="sec-title">功能模块</h3>
    <div class="mod-grid">
      <div v-for="m in modules" :key="m.key" class="mod-card" :style="{ background: m.bg }" @click="go(m.to)">
        <div class="mod-head">
          <span class="mod-icon">{{ m.icon }}</span>
          <span class="mod-arrow">→</span>
        </div>
        <div class="mod-title">{{ m.title }}</div>
        <div class="mod-desc">{{ m.desc }}</div>
        <div class="mod-tags">
          <span v-for="t in m.tags" :key="t" class="mod-tag">{{ t }}</span>
        </div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <h3 class="sec-title">快捷入口</h3>
    <el-card class="quick" shadow="never">
      <div class="quick-grid">
        <div v-for="q in quickLinks" :key="q.to" class="quick-item" @click="go(q.to)">
          <span class="quick-icon">{{ q.icon }}</span>
          <span class="quick-label">{{ q.label }}</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const isTeacher = computed(() => auth.currentUser?.role === 'teacher')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const modules = computed(() => ([
  {
    key: 'exam', icon: '📝', title: '在线考试',
    desc: isTeacher.value ? '创建考试 · 智能组卷 · 阅卷 · 成绩分析' : '我的考试 · 在线作答 · 成绩查询',
    to: isTeacher.value ? '/dashboard' : '/my-exams',
    tags: isTeacher.value ? ['考试管理', '题库', '阅卷'] : ['我的考试', '我的成绩'],
    bg: 'linear-gradient(135deg,#6d5efc,#4f7cff)'
  },
  {
    key: 'competition', icon: '🏆', title: '答题竞赛',
    desc: '限时竞赛 · 1v1 实时对战 · 段位勋章排行',
    to: '/competition',
    tags: ['竞赛广场', '1v1 PK', '段位'],
    bg: 'linear-gradient(135deg,#f0993a,#f5c04e)'
  },
  {
    key: 'bounty', icon: '💰', title: '征集悬赏',
    desc: '发布征集 · 众包投稿 · 采纳入库赚积分',
    to: '/bounty',
    tags: ['悬赏广场', '发布', '我的'],
    bg: 'linear-gradient(135deg,#22b573,#5fd08a)'
  },
  {
    key: 'learning', icon: '📚', title: '自主学习',
    desc: '错题本 · 自由刷题 · 学习计划 · 学情画像',
    to: '/learning',
    tags: ['错题本', '刷题', '报告'],
    bg: 'linear-gradient(135deg,#0ea5b7,#38c7d4)'
  }
]))

const quickLinks = computed(() => {
  const base = [
    { icon: '🏆', label: '竞赛中心', to: '/competition' },
    { icon: '🎖️', label: '段位中心', to: '/rank' },
    { icon: '🛡️', label: '战队中心', to: '/team' },
    { icon: '💰', label: '悬赏广场', to: '/bounty' },
    { icon: '📊', label: '学情画像', to: '/profile/study' },
    { icon: '📕', label: '错题本', to: '/learning/wrong-notebook' }
  ]
  return isTeacher.value
    ? [{ icon: '📊', label: '数据概览', to: '/dashboard' }, { icon: '📝', label: '考试管理', to: '/exams' }, ...base]
    : [{ icon: '📝', label: '我的考试', to: '/my-exams' }, { icon: '⚔️', label: '1v1 对战', to: '/competition/pk' }, ...base]
})

function go(to) { router.push(to) }
</script>

<style scoped>
.portal { max-width: 1120px; margin: 0 auto; padding: 4px 0 20px; }

/* 欢迎横幅 */
.welcome {
  position: relative; overflow: hidden;
  display: flex; align-items: center; justify-content: space-between;
  padding: 30px 36px; border-radius: 20px; margin-bottom: 30px;
  background: linear-gradient(135deg, #6d5efc 0%, #4f7cff 55%, #22c1c3 130%);
  color: #fff; box-shadow: 0 18px 40px rgba(91,107,255,.28);
}
.welcome::after {
  content: ''; position: absolute; right: -60px; top: -60px;
  width: 240px; height: 240px; border-radius: 50%;
  background: radial-gradient(circle, rgba(255,255,255,.22), transparent 70%);
}
.welcome-text h1 { font-size: 26px; margin: 0; font-weight: 800; letter-spacing: .5px; }
.welcome-text p { margin: 8px 0 0; opacity: .92; font-size: 14px; }
.welcome-role { position: relative; z-index: 1; }

/* 分区标题 */
.sec-title {
  font-size: 16px; font-weight: 800; color: var(--ink); margin: 0 0 16px;
  display: flex; align-items: center; gap: 8px;
}
.sec-title::before { content: ''; width: 4px; height: 16px; border-radius: 3px; background: var(--brand-grad); }

/* 模块网格 */
.mod-grid {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px; margin-bottom: 30px;
}
.mod-card {
  position: relative; color: #fff; border-radius: 18px; padding: 26px 24px;
  cursor: pointer; min-height: 176px; overflow: hidden;
  box-shadow: 0 12px 30px rgba(24,32,64,.14);
  transition: transform .2s, box-shadow .2s;
}
.mod-card::after {
  content: ''; position: absolute; right: -40px; bottom: -40px;
  width: 150px; height: 150px; border-radius: 50%;
  background: rgba(255,255,255,.12); transition: transform .3s;
}
.mod-card:hover { transform: translateY(-6px); box-shadow: 0 22px 44px rgba(24,32,64,.24); }
.mod-card:hover::after { transform: scale(1.5); }
.mod-head { display: flex; align-items: center; justify-content: space-between; }
.mod-icon { font-size: 40px; filter: drop-shadow(0 4px 8px rgba(0,0,0,.15)); }
.mod-arrow { font-size: 22px; opacity: .85; transition: transform .2s; }
.mod-card:hover .mod-arrow { transform: translateX(6px); }
.mod-title { font-size: 21px; font-weight: 800; margin: 14px 0 6px; }
.mod-desc { font-size: 13px; opacity: .94; line-height: 1.6; min-height: 34px; }
.mod-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; position: relative; z-index: 1; }
.mod-tag { font-size: 11px; padding: 3px 10px; border-radius: 999px; background: rgba(255,255,255,.22); backdrop-filter: blur(4px); }

/* 快捷入口 */
.quick { border: 1px solid var(--line); border-radius: var(--radius); }
.quick-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 14px; }
.quick-item {
  display: flex; align-items: center; gap: 10px; padding: 14px 16px;
  border-radius: 12px; cursor: pointer; background: var(--panel-2);
  border: 1px solid transparent; transition: all .2s; font-weight: 600; color: var(--ink-2);
}
.quick-item:hover { background: var(--surface); border-color: var(--el-color-primary-light-7); color: var(--el-color-primary-dark-2); box-shadow: var(--shadow-sm); transform: translateY(-2px); }
.quick-icon { font-size: 20px; }
.quick-label { font-size: 14px; }
</style>
