<template>
  <div v-loading="loading">
    <div class="page-header"><h2>PK 大厅</h2>
      <el-button :icon="Refresh" circle @click="load" />
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="等待中的挑战" name="waiting">
        <div style="margin-bottom:12px;">
          <el-select v-model="pickComp" placeholder="选择进行中的竞赛发起挑战" filterable style="width:320px;">
            <el-option v-for="c in pkCompetitions" :key="c.id" :value="c.id" :label="c.title" />
          </el-select>
          <el-button type="primary" style="margin-left:8px;" :disabled="!pickComp" @click="challenge">发起挑战</el-button>
        </div>
        <el-table :data="waiting" border>
          <el-table-column prop="competitionTitle" label="题源竞赛" />
          <el-table-column prop="challengerName" label="挑战者" width="140" />
          <el-table-column prop="createdAt" label="发起时间" width="180" :formatter="row => fmt(row.createdAt)" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button size="small" type="success" @click="accept(row)">应战</el-button></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!waiting.length" description="暂无等待中的挑战" />
      </el-tab-pane>

      <el-tab-pane label="我的对战" name="mine">
        <el-table :data="mine" border>
          <el-table-column prop="competitionTitle" label="题源竞赛" />
          <el-table-column prop="status" label="状态" width="100" :formatter="row => statusText(row.status)" />
          <el-table-column label="比分" width="140">
            <template #default="{ row }">{{ row.challengerScore }} : {{ row.opponentScore }}</template>
          </el-table-column>
          <el-table-column label="结果" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.status === 'finished'" :type="resultType(row)" size="small">{{ resultText(row) }}</el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }"><el-button size="small" @click="enter(row)">进入</el-button></template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!mine.length" description="还没有对战记录" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { pkAPI, competitionAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const tab = ref('waiting')
const waiting = ref([])
const mine = ref([])
const pkCompetitions = ref([])
const pickComp = ref(null)
let poll = null

function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '—' }
function statusText(s) { return { waiting: '等待中', playing: '进行中', finished: '已结束', cancelled: '已取消' }[s] || s }
function resultType(row) {
  const me = auth.currentUser?.id
  if (row.winnerId == null) return 'info'
  return row.winnerId === me ? 'success' : 'danger'
}
function resultText(row) {
  const me = auth.currentUser?.id
  if (row.winnerId == null) return '平局'
  return row.winnerId === me ? '胜利' : '失败'
}

async function load() {
  loading.value = true
  try {
    const lb = await pkAPI.lobby()
    waiting.value = lb.waiting || []
    mine.value = lb.mine || []
    const lobby = await competitionAPI.lobby()
    pkCompetitions.value = (lobby || []).filter(c => c.allowPk && c.status === 'ongoing')
  } catch (e) { /* noop */ } finally { loading.value = false }
}

async function challenge() {
  try { await pkAPI.create(pickComp.value, {}); ElMessage.success('已发起挑战'); load() } catch (e) { /* noop */ }
}
async function accept(row) {
  try { await pkAPI.accept(row.id); router.push(`/competition/pk/${row.id}`) } catch (e) { /* noop */ }
}
function enter(row) { router.push(`/competition/pk/${row.id}`) }

onMounted(() => { load(); poll = setInterval(load, 15000) })
onBeforeUnmount(() => { if (poll) clearInterval(poll) })
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
