<template>
  <div>
    <div class="page-header"><h2>竞赛中心</h2></div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="card in visibleCards" :key="card.path">
        <el-card shadow="hover" class="entry-card" @click="go(card)">
          <div style="display:flex;align-items:center;gap:12px;">
            <div :style="{ fontSize: '30px' }">{{ card.emoji }}</div>
            <div>
              <div style="font-weight:600;">{{ card.title }}</div>
              <div style="font-size:12px;color:#909399;margin-top:4px;">{{ card.desc }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:16px;" v-loading="loading">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <strong>我的竞技概览</strong>
          <el-tag v-if="rank" size="small" type="warning">{{ rank.tierIcon }} {{ rank.tier }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="4" border v-if="rank">
        <el-descriptions-item label="赛季">{{ rank.season }}</el-descriptions-item>
        <el-descriptions-item label="积分">{{ rank.points }}</el-descriptions-item>
        <el-descriptions-item label="赛季名次">{{ rank.mySeasonRank ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="距下一段位">{{ rank.progressToNext ?? '已满级' }}</el-descriptions-item>
        <el-descriptions-item label="PK 战绩">{{ rank.pkWin }}胜 / {{ rank.pkDraw }}平 / {{ rank.pkLose }}负</el-descriptions-item>
        <el-descriptions-item label="胜率">{{ rank.winRate }}%</el-descriptions-item>
        <el-descriptions-item label="当前连胜">{{ rank.streak }}</el-descriptions-item>
        <el-descriptions-item label="完赛场次">{{ rank.timedFinished }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无段位数据，快去参加一场竞赛吧" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { gamificationAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'
import { connectSocket } from '../../utils/socket'

const router = useRouter()
const auth = useAuthStore()
const rank = ref(null)
const loading = ref(false)

const allCards = [
  { emoji: '🏟️', title: '竞赛广场', desc: '浏览并参加限时积分赛', path: '/competition/plaza', everyone: true },
  { emoji: '⚔️', title: 'PK 大厅', desc: '1v1 实时对战', path: '/competition/pk', role: 'student' },
  { emoji: '🛠️', title: '竞赛管理', desc: '创建/发布/管理竞赛', path: '/competition/manage', role: 'teacher' },
  { emoji: '🏆', title: '段位中心', desc: '段位/赛季榜/勋章墙', path: '/rank', everyone: true },
  { emoji: '👥', title: '战队中心', desc: '创建与加入战队', path: '/team', everyone: true },
  { emoji: '📊', title: '学情画像', desc: '五维能力雷达', path: '/profile/study', everyone: true }
]

const visibleCards = computed(() => allCards.filter(c =>
  c.everyone || (c.role && auth.currentUser?.role === c.role)))

function go(card) {
  // 广场/大厅按角色分流：教师无参赛入口时仍进广场只读
  router.push(card.path)
}

async function load() {
  loading.value = true
  try { rank.value = await gamificationAPI.rankMe() }
  catch (e) { /* noop */ } finally { loading.value = false }
}

onMounted(() => { connectSocket(); load() })
</script>

<style scoped>
.entry-card { cursor: pointer; transition: transform .15s; }
.entry-card:hover { transform: translateY(-3px); }
</style>
