<template>
  <div>
    <div class="page-header">
      <h2>实时监考 - {{ title || '加载中' }}</h2>
      <div style="display: flex; gap: 8px; align-items: center;">
        <el-switch v-model="autoRefresh" active-text="10s 自动刷新" />
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" type="success" plain :loading="exporting" @click="exportExcel">导出成绩</el-button>
        <el-button size="small" @click="router.back()">返回</el-button>
      </div>
    </div>

    <el-alert
      :closable="false" type="info" style="margin-bottom: 12px;"
      :title="`共 ${rows.length} 名考生 · 进行中 ${counting('in_progress')} · 已交卷 ${counting('submitted')} · 最后更新 ${updatedAt}`" />

    <el-table :data="rows" stripe v-loading="loading">
      <el-table-column prop="studentName" label="考生" min-width="140" />
      <el-table-column prop="studentId" label="ID" width="90" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="轮次" width="80">
        <template #default="{ row }">第 {{ row.attemptNo || 1 }} 次</template>
      </el-table-column>
      <el-table-column label="答题进度" min-width="180">
        <template #default="{ row }">
          <el-progress
            :percentage="pct(row)" :stroke-width="14" text-inside
            :status="row.status === 'in_progress' ? '' : 'success'" />
          <span style="font-size: 12px; color: #909399;">{{ row.answeredCount || 0 }} / {{ row.totalCount || 0 }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="switchCount" label="切屏" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="(row.switchCount || 0) >= 3 ? 'danger' : 'info'">{{ row.switchCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="本轮得分" width="110">
        <template #default="{ row }">{{ row.sessionScore != null ? row.sessionScore : '-' }}</template>
      </el-table-column>
      <el-table-column label="开始时间" width="180">
        <template #default="{ row }">{{ fmt(row.startTime) }}</template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { examAPI } from '../api/endpoints'

const router = useRouter()
const route = useRoute()
const examId = route.params.id

const rows = ref([])
const title = ref('')
const loading = ref(false)
const exporting = ref(false)
const autoRefresh = ref(true)
const updatedAt = ref('-')
let timer = null

onMounted(async () => {
  try {
    const exam = await examAPI.get(examId).catch(() => null)
    title.value = exam?.title || ''
  } catch (e) { /* 标题可缺省 */ }
  await load()
  startTimer()
})
onUnmounted(() => stopTimer())

watch(autoRefresh, v => { v ? startTimer() : stopTimer() })

function startTimer() { stopTimer(); if (autoRefresh.value) timer = setInterval(load, 10000) }
function stopTimer() { if (timer) { clearInterval(timer); timer = null } }

async function load() {
  loading.value = true
  try {
    rows.value = await examAPI.monitor(examId) || []
    updatedAt.value = new Date().toLocaleTimeString()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '监考数据加载失败')
  } finally {
    loading.value = false
  }
}

async function exportExcel() {
  exporting.value = true
  try {
    const res = await examAPI.exportResults(examId)
    const blob = new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `exam_${examId}_results.xlsx`
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.URL.revokeObjectURL(url)
    ElMessage.success('已导出成绩表')
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

function pct(row) {
  const t = row.totalCount || 0
  if (!t) return 0
  return Math.round(((row.answeredCount || 0) / t) * 100)
}
function counting(s) { return rows.value.filter(r => r.status === s).length }
function statusLabel(s) {
  return { in_progress: '进行中', submitted: '已交卷', expired: '已超时', switched: '异常切屏', not_started: '未开始' }[s] || (s || '-')
}
function statusType(s) {
  return { in_progress: 'warning', submitted: 'success', expired: 'info', switched: 'danger', not_started: 'info' }[s] || 'info'
}
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 19) : '-' }
</script>
