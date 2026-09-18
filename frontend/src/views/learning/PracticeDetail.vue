<template>
  <div v-loading="loading">
    <div class="page-header" v-if="detail">
      <h2>{{ detail.title }}</h2>
      <div>
        <el-tag :type="isFinished ? 'success' : 'warning'" style="margin-right: 12px;">
          {{ isFinished ? '已完成' : '进行中' }}
        </el-tag>
        <span style="color: #606266; margin-right: 16px;">
          已答 {{ answeredCount }}/{{ detail.questions.length }} · 答对 {{ correctCount }}
        </span>
        <el-button v-if="!isFinished" type="primary" @click="submitAll">完成练习</el-button>
        <el-button @click="router.push('/learning/practice')">返回列表</el-button>
      </div>
    </div>

    <div v-if="isFinished && summary" class="summary-card">
      <el-result icon="success" :title="`练习完成：${summary.correctCount} / ${summary.questionsCount} 答对`"
                 :sub-title="`正确率 ${(summary.accuracy * 100).toFixed(1)}%，用时 ${Math.round(summary.totalTimeSec / 60)} 分钟。错题已自动收入错题本。`">
        <template #extra>
          <el-button type="primary" @click="router.push('/learning/wrong-notebook')">查看错题本</el-button>
          <el-button @click="router.push('/learning/practice')">再练一组</el-button>
        </template>
      </el-result>
    </div>

    <el-card v-for="(q, idx) in (detail?.questions || [])" :key="q.questionId" style="margin-bottom: 14px;">
      <div style="display: flex; justify-content: space-between; align-items: flex-start;">
        <div style="font-weight: 600; line-height: 1.6;">
          {{ idx + 1 }}. [{{ typeLabel(q.type) }}] {{ q.content }}
        </div>
        <el-tag v-if="q.isCorrect === true" type="success" size="small">答对</el-tag>
        <el-tag v-else-if="q.isCorrect === false" type="danger" size="small">答错</el-tag>
      </div>

      <div v-if="q.options && q.options.length" style="margin: 10px 0 4px; color: #606266;">
        <div v-for="(opt, i) in q.options" :key="i" style="margin: 3px 0;">
          <el-tag size="small" type="info" style="margin-right: 8px;">{{ String.fromCharCode(65 + i) }}</el-tag>{{ opt }}
        </div>
      </div>

      <!-- 作答区 -->
      <div style="margin-top: 10px;" v-if="!isFinished">
        <el-radio-group v-if="q.type === 'single_choice'" v-model="answers[q.questionId].value" @change="submitAnswer(q)">
          <el-radio v-for="(opt, i) in q.options" :key="i" :value="String.fromCharCode(65 + i)">
            {{ String.fromCharCode(65 + i) }}
          </el-radio>
        </el-radio-group>
        <el-checkbox-group v-else-if="q.type === 'multiple_choice'" v-model="answers[q.questionId].multi" @change="submitMulti(q)">
          <el-checkbox v-for="(opt, i) in q.options" :key="i" :value="String.fromCharCode(65 + i)">
            {{ String.fromCharCode(65 + i) }}
          </el-checkbox>
        </el-checkbox-group>
        <el-radio-group v-else-if="q.type === 'true_false'" v-model="answers[q.questionId].value" @change="submitAnswer(q)">
          <el-radio value="true">对</el-radio>
          <el-radio value="false">错</el-radio>
        </el-radio-group>
        <div v-else style="display: flex; gap: 8px; max-width: 480px;">
          <el-input v-model="answers[q.questionId].value" placeholder="输入答案后回车提交" @keyup.enter="submitAnswer(q)" />
          <el-button type="primary" plain @click="submitAnswer(q)">提交</el-button>
        </div>
      </div>

      <!-- 即时反馈 -->
      <el-alert v-if="feedback[q.questionId]" :type="feedback[q.questionId].isCorrect ? 'success' : 'error'"
                :closable="false" show-icon style="margin-top: 10px;">
        <template #title>
          {{ feedback[q.questionId].isCorrect ? '答对了' : '答错了' }}
          · 正确答案：{{ feedback[q.questionId].correctAnswer || '-' }}
        </template>
        <div v-if="feedback[q.questionId].analysis">解析：{{ feedback[q.questionId].analysis }}</div>
      </el-alert>
      <el-alert v-else-if="isFinished && q.isCorrect === false" type="error" :closable="false" show-icon
                style="margin-top: 10px;" title="答错（重进页面仅显示判定结果，详情见错题本）" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { learningAPI } from '../../api/endpoints'

const route = useRoute()
const router = useRouter()
const sessionId = route.params.id

const detail = ref(null)
const loading = ref(false)
const summary = ref(null)
const answers = reactive({})     // qid -> { value, multi, answeredAt }
const feedback = reactive({})    // qid -> { isCorrect, correctAnswer, analysis }

const isFinished = computed(() => detail.value?.status === 'completed')
const answeredCount = computed(() =>
  (detail.value?.questions || []).filter(q => q.studentAnswer).length)
const correctCount = computed(() =>
  (detail.value?.questions || []).filter(q => q.isCorrect === true).length)

const TYPE_LABELS = {
  single_choice: '单选题', multiple_choice: '多选题', fill_blank: '填空题', true_false: '判断题'
}
function typeLabel(t) { return TYPE_LABELS[t] || t }

async function load() {
  loading.value = true
  try {
    detail.value = await learningAPI.practiceDetail(sessionId)
    for (const q of detail.value.questions) {
      const saved = q.studentAnswer || ''
      answers[q.questionId] = {
        value: q.type === 'multiple_choice' ? '' : saved,
        multi: q.type === 'multiple_choice' ? saved.split(/[,，]/).filter(Boolean) : [],
        answeredAt: q.studentAnswer ? Date.now() : null
      }
    }
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

function timeSpent(qid) {
  const at = answers[qid]?.answeredAt
  return at ? Math.max(0, Math.round((Date.now() - at) / 1000)) : null
}

async function submitAnswer(q) {
  const a = answers[q.questionId]
  if (!a.value) return
  if (a.answeredAt === null) a.answeredAt = Date.now()
  try {
    const res = await learningAPI.practiceAnswer(sessionId, {
      questionId: q.questionId, studentAnswer: a.value, timeSpentSec: timeSpent(q.questionId)
    })
    feedback[q.questionId] = res
    q.studentAnswer = a.value
    q.isCorrect = res.isCorrect
  } catch (e) {}
}

async function submitMulti(q) {
  const a = answers[q.questionId]
  if (!a.multi.length) return
  if (a.answeredAt === null) a.answeredAt = Date.now()
  try {
    const res = await learningAPI.practiceAnswer(sessionId, {
      questionId: q.questionId, studentAnswer: a.multi.join(','), timeSpentSec: timeSpent(q.questionId)
    })
    feedback[q.questionId] = res
    q.studentAnswer = a.multi.join(',')
    q.isCorrect = res.isCorrect
  } catch (e) {}
}

async function submitAll() {
  const total = detail.value.questions.length
  if (answeredCount.value < total) {
    try {
      await ElMessageBox.confirm(`还有 ${total - answeredCount.value} 题未作答，确定完成练习？`, '提示', { type: 'warning' })
    } catch (e) { return }
  }
  try {
    summary.value = await learningAPI.practiceSubmit(sessionId)
    await load()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (e) {}
}

onMounted(load)
</script>

<style scoped>
.summary-card { margin-bottom: 14px; }
</style>
