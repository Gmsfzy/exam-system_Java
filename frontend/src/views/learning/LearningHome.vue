<template>
  <div>
    <div class="page-header">
      <h2>自主学习中心</h2>
    </div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="card in cards" :key="card.path">
        <el-card shadow="hover" class="entry-card" @click="router.push(card.path)">
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-icon :size="30" :color="card.color"><component :is="card.icon" /></el-icon>
            <div>
              <div style="font-weight: 600;">{{ card.title }}</div>
              <div style="font-size: 12px; color: #909399; margin-top: 4px;">{{ card.desc }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px;" v-loading="loadingOverview">
      <template #header><strong>学习概览</strong></template>
      <el-descriptions :column="4" border v-if="overview">
        <el-descriptions-item label="累计学习题数">{{ overview.totalQuestions }}</el-descriptions-item>
        <el-descriptions-item label="综合正确率">{{ (overview.accuracy * 100).toFixed(1) }}%</el-descriptions-item>
        <el-descriptions-item label="累计学习时长">{{ formatDuration(overview.totalTimeSec) }}</el-descriptions-item>
        <el-descriptions-item label="学习天数">{{ overview.studyDays }} 天</el-descriptions-item>
        <el-descriptions-item label="待攻克错题">{{ overview.wrongPendingCount }}</el-descriptions-item>
        <el-descriptions-item label="错题总数">{{ overview.wrongTotalCount }}</el-descriptions-item>
        <el-descriptions-item label="进行中计划">{{ overview.activePlanCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card style="margin-top: 16px;">
      <template #header><strong>AI 智能答疑</strong></template>
      <el-alert
        type="info" :closable="false" show-icon style="margin-bottom: 12px;"
        title="遇到不会的知识点，随时问「智汇小助教」。可结合错题本中的具体题目提问。" />
      <el-select v-model="askQuestionId" clearable filterable placeholder="（可选）结合某道错题提问"
                 style="width: 100%; margin-bottom: 8px;">
        <el-option v-for="w in wrongSample" :key="w.id" :value="w.questionId"
                   :label="(w.questionContent || ('题目 #' + w.questionId)).slice(0, 50)" />
      </el-select>
      <el-input v-model="askText" type="textarea" :rows="3" maxlength="2000" show-word-limit
                placeholder="输入你的问题，例如：多选题总是漏选怎么办？" />
      <div style="margin-top: 8px; text-align: right;">
        <el-button type="primary" :loading="asking" @click="sendAsk">提问</el-button>
      </div>
      <el-card v-if="askAnswer" shadow="never" style="margin-top: 8px; white-space: pre-wrap; background: #f8f9fb;">
        {{ askAnswer }}
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, markRaw } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { learningAPI } from '../../api/endpoints'
import { WarningFilled, Basketball, Calendar, DataAnalysis } from '@element-plus/icons-vue'

const router = useRouter()
const loadingOverview = ref(false)
const overview = ref(null)
const wrongSample = ref([])

const askQuestionId = ref(null)
const askText = ref('')
const asking = ref(false)
const askAnswer = ref('')

const cards = [
  { title: '错题本', desc: '回顾并攻克做错的题目', path: '/learning/wrong-notebook', icon: markRaw(WarningFilled), color: '#f56c6c' },
  { title: '自由刷题', desc: '按专业/课程抽题，即时判分', path: '/learning/practice', icon: markRaw(Basketball), color: '#409eff' },
  { title: '学习计划', desc: '设定目标，追踪完成进度', path: '/learning/plans', icon: markRaw(Calendar), color: '#e6a23c' },
  { title: '学习报告', desc: '每日学习数据聚合分析', path: '/learning/report', icon: markRaw(DataAnalysis), color: '#67c23a' }
]

function formatDuration(sec) {
  if (!sec || sec <= 0) return '0 分钟'
  const h = Math.floor(sec / 3600)
  const m = Math.round((sec % 3600) / 60)
  return h > 0 ? `${h} 小时 ${m} 分钟` : `${m} 分钟`
}

async function loadOverview() {
  loadingOverview.value = true
  try {
    overview.value = await learningAPI.reportOverview()
    wrongSample.value = (await learningAPI.wrongRecords({ mastered: false })) || []
  } catch (e) { /* 拦截器已提示 */ } finally {
    loadingOverview.value = false
  }
}

async function sendAsk() {
  if (!askText.value.trim()) { ElMessage.warning('请输入问题'); return }
  asking.value = true
  askAnswer.value = ''
  try {
    const res = await learningAPI.ask({ questionId: askQuestionId.value || null, question: askText.value.trim() })
    askAnswer.value = res?.answer || ''
  } catch (e) { /* 拦截器已提示 */ } finally {
    asking.value = false
  }
}

onMounted(loadOverview)
</script>

<style scoped>
.entry-card { cursor: pointer; transition: transform 0.15s; }
.entry-card:hover { transform: translateY(-3px); }
</style>
