<template>
  <div v-if="result" style="max-width: 960px; margin: 0 auto; padding: 20px;">
    <div class="page-header">
      <h2>成绩详情 - {{ result.examTitle }}</h2>
      <el-button @click="router.back()">返回</el-button>
    </div>

    <el-alert
      v-if="result.grading"
      type="warning"
      :closable="false"
      show-icon
      title="主观题正在 AI 评分中"
      description="当前展示为客观题得分，最终成绩将在评分完成后自动刷新，请稍候。"
      style="margin-bottom: 16px;"
    />

    <el-card style="margin-bottom: 20px;">
      <el-descriptions :column="2" border>
        <el-descriptions-item v-if="isTeacher" label="学生">{{ result.studentName }}</el-descriptions-item>
        <el-descriptions-item label="成绩">
          <strong :style="{ fontSize: '18px', color: scoreColor }">
            {{ result.score }} / {{ result.totalScore }}
            <span style="font-size: 13px; color: #909399; margin-left: 8px;">
              ({{ pct }}%)
            </span>
          </strong>
        </el-descriptions-item>
        <el-descriptions-item label="提交时间" :span="2">{{ result.submittedAt }}</el-descriptions-item>
        <el-descriptions-item v-if="result.aiAnalysis" label="AI 分析" :span="2">
          <div style="white-space: pre-wrap;">{{ result.aiAnalysis }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <h3 style="margin-bottom: 12px;">答题详情</h3>
    <el-empty v-if="!(result.answers && result.answers.length)" description="答案尚未发布，暂不可见" />
    <div v-for="(a, idx) in (result.answers || [])" :key="a.questionId || idx" class="question-card">
      <div style="margin-bottom: 12px;">
        <span class="chip gray">第 {{ idx + 1 }} 题</span>
        <span class="chip">{{ typeLabel(a.type) }}</span>
        <span class="chip orange">{{ a.maxScore }} 分</span>
        <span v-if="isGrading(a)" class="chip blue" style="float: right;">⏳ AI 评分中</span>
        <span v-else-if="a.needsManualGrade" class="chip orange" style="float: right;">待人工评阅</span>
        <span v-else-if="a.isCorrect" class="chip green" style="float: right;">✓ 正确（{{ a.score }} 分）</span>
        <span v-else class="chip red" style="float: right;">✗ 错误（{{ a.score }} 分）</span>
      </div>
      <h4>{{ a.content }}</h4>
      <div style="margin: 12px 0; padding: 10px; background: var(--surface-2); border-radius: 4px;">
        <strong>学生答案：</strong><br>
        <span style="white-space: pre-wrap;">{{ a.studentAnswer || '(未作答)' }}</span>
      </div>
      <div style="margin: 12px 0; padding: 10px; background: var(--wash-blue); border-radius: 4px;">
        <strong>参考答案：</strong><br>
        <span style="white-space: pre-wrap;">{{ a.correctAnswer || '-' }}</span>
      </div>
      <div v-if="a.analysis" style="margin: 12px 0; padding: 10px; background: var(--wash-cyan); border-radius: 4px; border-left: 4px solid var(--brand-3); line-height: 1.6; color: var(--ink-2);">
        <strong>解析：</strong>{{ a.analysis }}
      </div>

      <div v-if="isTeacher && a.needsManualGrade" style="padding: 12px; background: var(--wash-orange); border-radius: 4px; border-left: 4px solid var(--el-color-warning);">
        <div style="margin-bottom: 8px;">
          <strong>教师评分：</strong>
          <el-input-number v-model="a._score" :min="0" :max="a.maxScore" size="small" style="width: 140px; margin-left: 8px;" />
          <span style="margin-left: 8px; color: #909399;">/ {{ a.maxScore }} 分</span>
        </div>
        <el-input v-model="a._comment" type="textarea" :rows="2" placeholder="评语（可选）" style="margin-bottom: 8px;" />
        <el-button size="small" type="primary" :loading="savingId === a.questionId" @click="gradeAnswer(a)">提交人工评分</el-button>
      </div>
    </div>
  </div>
  <div v-else style="text-align: center; padding: 60px;">
    <el-icon :size="40" color="#909399"><Loading /></el-icon>
    <p>加载中...</p>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resultAPI } from '../api/endpoints'
import { useAuthStore } from '../stores/auth'
import { Loading } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const result = ref(null)
const savingId = ref(null)
let pollTimer = null
const isTeacher = computed(() => authStore.currentUser?.role === 'teacher')
const pct = computed(() => {
  if (!result.value || !result.value.totalScore) return '0.0'
  return ((result.value.score / result.value.totalScore) * 100).toFixed(1)
})
const scoreColor = computed(() => {
  if (!result.value) return '#303133'
  return result.value.score / result.value.totalScore >= 0.6 ? '#67c23a' : '#f56c6c'
})

onMounted(load)
onBeforeUnmount(() => { if (pollTimer) clearInterval(pollTimer) })

function isGrading(a) {
  return a.gradeStatus === 'pending_ai' || a.gradeStatus === 'grading'
}

async function load() {
  try {
    const r = await resultAPI.get(route.params.id)
    if (r.answers) {
      r.answers.forEach(a => {
        a._score = a.score != null ? a.score : 0
        a._comment = ''
      })
    }
    result.value = r
    schedulePoll(r.grading)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  }
}

// AI 异步评分未完成时，每 5s 静默刷新一次，完成后停止
function schedulePoll(grading) {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  if (!grading) return
  pollTimer = setInterval(async () => {
    try {
      const r = await resultAPI.get(route.params.id)
      result.value = r
      if (!r.grading) {
        clearInterval(pollTimer); pollTimer = null
        ElMessage.success('主观题评分完成，成绩已更新')
      }
    } catch (e) { /* 下次轮询重试 */ }
  }, 5000)
}

async function gradeAnswer(a) {
  savingId.value = a.questionId
  try {
    await resultAPI.manualGrade(result.value.examId, result.value.studentId, {
      questionId: a.questionId,
      manualScore: a._score,
      manualComment: a._comment
    })
    ElMessage.success('评分已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    savingId.value = null
  }
}

function typeLabel(t) {
  return { single_choice: '单选', multiple_choice: '多选', true_false: '判断',
    fill_blank: '填空', short_answer: '简答', programming: '编程题',
    calculation: '计算题', application: '应用题' }[t] || t
}
</script>

<style scoped>
/* 复用全局主题感知的 .question-card / .chip 样式（自动适配亮暗） */
</style>
