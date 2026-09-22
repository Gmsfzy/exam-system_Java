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
        <el-descriptions-item label="轮次">第 {{ result.attemptNo || 1 }} 次</el-descriptions-item>
        <el-descriptions-item label="成绩">
          <span v-if="result.published === false" style="color: #e6a23c;">
            <el-tag size="small" type="warning">未发布</el-tag>
            <span style="margin-left: 6px;">成绩尚未公布</span>
          </span>
          <strong v-else :style="{ fontSize: '18px', color: scoreColor }">
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

    <!-- 申诉 / 复核区 -->
    <el-card v-if="showReviewCard" style="margin-bottom: 20px;">
      <div style="display: flex; align-items: center; gap: 12px; flex-wrap: wrap;">
        <strong>成绩申诉</strong>
        <el-tag size="small" :type="reviewType">{{ reviewLabel }}</el-tag>
      </div>
      <div v-if="result.reviewReason" style="margin-top: 10px; color: var(--ink-2);">申诉理由：{{ result.reviewReason }}</div>
      <div v-if="result.reviewReply" style="margin-top: 6px; color: #67c23a;">教师回复：{{ result.reviewReply }}</div>

      <!-- 学生：发起申诉 -->
      <div v-if="!isTeacher && canStudentReview" style="margin-top: 12px;">
        <el-input v-model="reviewReason" type="textarea" :rows="2" placeholder="请说明申诉理由（如判分有误）" style="margin-bottom: 8px;" />
        <el-button type="primary" size="small" :loading="reviewing" @click="submitReview">提交申诉</el-button>
      </div>

      <!-- 教师：处理待审申诉 -->
      <div v-if="isTeacher && result.reviewStatus === 'pending'" style="margin-top: 12px;">
        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px;">
          <span>改后总分：</span>
          <el-input-number v-model="handleScore" :min="0" :max="result.totalScore || 100" size="small" />
          <el-input v-model="handleReply" placeholder="处理回复" size="small" style="flex: 1; min-width: 200px;" />
        </div>
        <el-button type="success" size="small" :loading="reviewing" @click="handleReview('approve')">批准并改分</el-button>
        <el-button type="info" size="small" :loading="reviewing" @click="handleReview('reject')">驳回</el-button>
      </div>
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
// M6 申诉状态
const reviewing = ref(false)
const reviewReason = ref('')
const handleScore = ref(0)
const handleReply = ref('')
const pct = computed(() => {
  if (!result.value || !result.value.totalScore) return '0.0'
  return ((result.value.score / result.value.totalScore) * 100).toFixed(1)
})
const scoreColor = computed(() => {
  if (!result.value) return '#303133'
  return result.value.score / result.value.totalScore >= 0.6 ? '#67c23a' : '#f56c6c'
})
const reviewLabel = computed(() => ({ pending: '申诉中', approved: '已改分', rejected: '申诉驳回' }[result.value?.reviewStatus] || '可申诉'))
const reviewType = computed(() => ({ pending: 'warning', approved: 'success', rejected: 'info' }[result.value?.reviewStatus] || ''))
// 学生仅当成绩已发布且未在申诉/未驳回时（驳回后可再次申诉）发起
const canStudentReview = computed(() => {
  const r = result.value
  if (!r || r.published === false || r.grading) return false
  return !r.reviewStatus || r.reviewStatus === 'none' || r.reviewStatus === 'rejected'
})
// 申诉卡片展示：已有申诉状态，或学生可发起，或教师待处理
const showReviewCard = computed(() => {
  const r = result.value
  if (!r) return false
  if (isTeacher.value) return r.reviewStatus && r.reviewStatus !== 'none'
  return r.published !== false && (canStudentReview.value || (r.reviewStatus && r.reviewStatus !== 'none'))
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
    handleScore.value = r.score != null ? r.score : 0
    schedulePoll(r.grading)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  }
}

async function submitReview() {
  if (!reviewReason.value.trim()) { ElMessage.warning('请填写申诉理由'); return }
  reviewing.value = true
  try {
    await resultAPI.requestReview(route.params.id, { reason: reviewReason.value })
    ElMessage.success('申诉已提交，请等待教师处理')
    reviewReason.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '申诉提交失败')
  } finally {
    reviewing.value = false
  }
}

async function handleReview(action) {
  if (action === 'approve' && handleScore.value == null) { ElMessage.warning('请填写改后总分'); return }
  reviewing.value = true
  try {
    await resultAPI.handleReview(route.params.id, {
      action,
      newScore: action === 'approve' ? handleScore.value : null,
      reply: handleReply.value
    })
    ElMessage.success(action === 'approve' ? '已批准并改分' : '已驳回申诉')
    handleReply.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '处理失败')
  } finally {
    reviewing.value = false
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
