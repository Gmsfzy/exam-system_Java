<template>
  <div>
    <div class="page-header">
      <h2>成绩一览</h2>
      <div style="display: flex; gap: 8px;">
        <el-select v-model="examId" placeholder="选择考试" style="width: 280px;" @change="load">
          <el-option v-for="e in exams" :key="e.id" :label="e.title" :value="e.id" />
        </el-select>
        <el-button type="primary" :disabled="!examId" @click="showAnalysis = true">成绩分析</el-button>
      </div>
    </div>

    <el-empty v-if="!examId" description="请先选择一场考试" />
    <el-empty v-else-if="list.length === 0" description="该考试暂无提交记录" />
    <el-table v-else :data="list" stripe>
      <el-table-column prop="id" label="成绩ID" width="90" />
      <el-table-column prop="examTitle" label="考试" min-width="200" />
      <el-table-column label="成绩" width="200">
        <template #default="{ row }">
          <strong>{{ row.score }} / {{ row.totalScore }}</strong>
          <span style="margin-left: 8px; color: #909399;">
            ({{ pct(row) }}%)
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="200" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" @click="router.push('/results/' + row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showAnalysis" title="成绩分析" width="640px">
      <div v-if="analysis">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="应考人数">{{ analysis.studentCount }}</el-descriptions-item>
          <el-descriptions-item label="已提交">{{ analysis.submittedCount }}</el-descriptions-item>
          <el-descriptions-item label="平均分">{{ analysis.averageScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="及格线">{{ analysis.passLine ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高分">{{ analysis.maxScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最低分">{{ analysis.minScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="及格率" :span="2">
            {{ analysis.passRate != null ? analysis.passRate.toFixed(1) + '%' : '-' }}
          </el-descriptions-item>
        </el-descriptions>
        <h4 style="margin: 16px 0 8px;">分数段分布</h4>
        <el-table :data="analysis.scoreDistribution || []" size="small" stripe>
          <el-table-column v-for="col in distCols" :key="col" :prop="col" :label="col" />
        </el-table>
      </div>
      <el-empty v-else description="暂无分析数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { resultAPI, examAPI } from '../api/endpoints'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const exams = ref([])
const examId = ref(route.query.examId ? Number(route.query.examId) : null)
const list = ref([])
const analysis = ref(null)
const showAnalysis = ref(false)
const distCols = ref([])

onMounted(async () => {
  try {
    exams.value = await examAPI.list() || []
    if (!examId.value && exams.value.length) examId.value = exams.value[0].id
    if (examId.value) await load()
  } catch (e) {}
})

async function load() {
  if (!examId.value) return
  analysis.value = null
  try {
    list.value = await resultAPI.examResults(examId.value) || []
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '成绩加载失败')
  }
}

function pct(row) {
  if (!row.totalScore) return '0.0'
  return ((row.score / row.totalScore) * 100).toFixed(1)
}

// 打开分析弹窗时加载统计
watch(showAnalysis, async v => {
  if (!v || !examId.value) return
  try {
    analysis.value = await resultAPI.analysis(examId.value)
    const rows = analysis.value?.scoreDistribution || []
    if (rows.length) distCols.value = Object.keys(rows[0])
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '分析加载失败')
  }
})
</script>
