<template>
  <div>
    <div class="page-header"><h2>竞赛广场</h2>
      <el-button :icon="Refresh" circle @click="load" />
    </div>

    <el-row :gutter="16" v-loading="loading">
      <el-col :span="8" v-for="c in items" :key="c.id" style="margin-bottom:16px;">
        <el-card shadow="hover">
          <div style="display:flex;justify-content:space-between;align-items:flex-start;">
            <div style="font-weight:600;font-size:16px;">{{ c.title }}</div>
            <el-tag :type="statusType(c.status)" size="small">{{ statusText(c.status) }}</el-tag>
          </div>
          <div style="color:#909399;font-size:12px;margin:8px 0;min-height:32px;">{{ c.description || '—' }}</div>
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="时长">{{ c.duration }} 分钟</el-descriptions-item>
            <el-descriptions-item label="抽题数">{{ c.drawCount > 0 ? c.drawCount : '全部' }}</el-descriptions-item>
            <el-descriptions-item label="总分">{{ c.totalScore }}</el-descriptions-item>
            <el-descriptions-item label="参与">{{ c.participantCount }} 人</el-descriptions-item>
            <el-descriptions-item label="PK">{{ c.allowPk ? '开放' : '关闭' }}</el-descriptions-item>
            <el-descriptions-item label="我的名次">{{ c.myRank ?? '—' }}</el-descriptions-item>
          </el-descriptions>
          <div style="margin-top:12px;display:flex;gap:8px;">
            <el-button size="small" @click="viewLeaderboard(c)">查看榜单</el-button>
            <template v-if="isStudent">
              <el-button v-if="!c.joined && c.status !== 'ended'" size="small" type="primary" @click="join(c)">报名</el-button>
              <el-button v-else-if="c.joined && c.status === 'ongoing'" size="small" type="success" @click="take(c)">进入答题</el-button>
            </template>
          </div>
        </el-card>
      </el-col>
      <el-col :span="24" v-if="!loading && !items.length"><el-empty description="暂无竞赛" /></el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { competitionAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const isStudent = computed(() => auth.currentUser?.role === 'student')
const items = ref([])
const loading = ref(false)

function statusType(s) { return { published: 'info', ongoing: 'success', ended: 'info', draft: 'warning' }[s] || 'info' }
function statusText(s) { return { published: '未开始', ongoing: '进行中', ended: '已结束', draft: '草稿' }[s] || s }

async function load() {
  loading.value = true
  try { items.value = await competitionAPI.lobby() || [] }
  catch (e) { /* noop */ } finally { loading.value = false }
}

async function join(c) {
  try {
    await competitionAPI.join(c.id)
    ElMessage.success('报名成功')
    load()
  } catch (e) { /* noop */ }
}

function take(c) { router.push(`/competition/take/${c.id}`) }
function viewLeaderboard(c) { router.push(`/competition/${c.id}/leaderboard`) }

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
