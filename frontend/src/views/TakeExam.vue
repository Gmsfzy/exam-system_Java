<template>
  <div v-if="started" style="max-width: 900px; margin: 0 auto; padding: 20px;">
    <div class="exam-timer">
      ⏱ 剩余时间 {{ formatTime(remaining) }}
    </div>

    <div v-if="!submitted" class="exam-info">
      <h3>{{ exam.title }}</h3>
      <p>
        时长：{{ exam.duration }} 分钟
        &nbsp;|&nbsp; 题目：{{ questions.length }} 题
        &nbsp;|&nbsp; 总分：{{ totalScore }} 分
      </p>
      <p style="color: #e6a23c;">⚠ 切屏将被记录，请认真作答。题目答案会自动保存。</p>
    </div>

    <div v-if="submitted" class="exam-info" style="background: #f0f9eb; border-color: #c2e7b0;">
      <h3 style="color: #67c23a;">✅ 已提交试卷</h3>
      <p>客观题得分：<strong>{{ result.score }} / {{ result.totalScore }}</strong> 分</p>
      <p v-if="result.grading" style="color: #e6a23c;">⏳ 主观题正在 AI 评分中，最终成绩将在完成后自动更新，请稍后到“我的成绩”查看。</p>
      <p v-else-if="result.hasSubjective" style="color: #e6a23c;">试卷包含主观题，将由教师人工评阅后更新最终成绩。</p>
      <div style="margin-top: 12px;">
        <el-button type="primary" @click="goResults">查看成绩</el-button>
        <el-button @click="router.push('/my-exams')">返回</el-button>
      </div>
    </div>

    <template v-if="!submitted">
      <div v-for="(q, idx) in questions" :key="q.questionId" class="question-card">
        <h4>
          <span class="chip gray">{{ idx + 1 }}</span>
          <span class="chip">{{ typeLabel(q.type) }}</span>
          <span class="chip orange">{{ diffLabel(q.difficulty) }}</span>
          <span v-if="q.isAnswered" class="chip green">已答</span>
          <span style="float: right; font-size: 14px; color: #909399;">{{ q.score }} 分</span>
          <div style="margin-top: 12px; font-size: 16px; font-weight: 500; color: #303133;">{{ q.content }}</div>
        </h4>

        <div v-if="q.options && q.options.length" class="options">
          <div
            v-for="(opt, i) in q.options"
            :key="i"
            :class="{ selected: isSelected(q, String.fromCharCode(65 + i)) }"
            @click="selectOption(q, String.fromCharCode(65 + i))"
          >
            <strong>{{ String.fromCharCode(65 + i) }}.</strong> {{ opt }}
          </div>
        </div>

        <textarea
          v-else
          v-model="answers[q.questionId]"
          placeholder="请在此处输入你的答案..."
          @blur="saveAnswer(q.questionId)"
          @change="saveAnswer(q.questionId)"
        ></textarea>
      </div>

      <div style="text-align: center; padding: 24px;">
        <el-button type="primary" size="large" @click="submitExam">提交试卷</el-button>
      </div>
    </template>
  </div>
  <div v-else style="text-align: center; padding: 60px;">
    <el-icon :size="40" color="#909399"><Loading /></el-icon>
    <p style="margin-top: 12px;">{{ loadError || '正在加载考试...' }}</p>
    <el-button v-if="loadError" type="primary" style="margin-top: 16px;" @click="router.push('/my-exams')">返回我的考试</el-button>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { takeAPI } from '../api/endpoints'
import { Loading } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const examId = route.params.examId

const started = ref(false)
const loadError = ref('')
const exam = ref({ title: '', duration: 60 })
const sessionId = ref(null)
const questions = ref([])
const answers = reactive({})
const remaining = ref(0)
const submitted = ref(false)
const result = ref({ score: 0, totalScore: 0, hasSubjective: false, grading: false })
let timer = null
let lastHidden = false
let lastSwitchAt = 0

const totalScore = computed(() => questions.value.reduce((s, q) => s + (q.score || 0), 0))

onMounted(async () => {
  try {
    // 1. 开始/恢复会话（幂等：已有进行中会话则复用）
    const startedSession = await takeAPI.start(examId)
    sessionId.value = startedSession.sessionId
    // 2. 拉取试卷内容
    const data = await takeAPI.take(examId)
    sessionId.value = data.sessionId
    exam.value = { title: data.title, duration: data.duration || 60 }
    questions.value = data.questions || []
    for (const q of questions.value) {
      if (q.studentAnswer) answers[q.questionId] = q.studentAnswer
    }
    remaining.value = (data.duration || 60) * 60
    started.value = true
    timer = setInterval(tick, 1000)
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('blur', onBlur)
  } catch (e) {
    loadError.value = e.response?.data?.message || '无法开始考试'
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  cleanupListeners()
})

function cleanupListeners() {
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('blur', onBlur)
}

function tick() {
  if (remaining.value <= 0) {
    clearInterval(timer)
    if (!submitted.value) submitExam(true)
    return
  }
  remaining.value--
}

function onVisibility() {
  if (document.hidden && !lastHidden) {
    lastHidden = true
    reportSwitchScreen()
  } else if (!document.hidden) {
    lastHidden = false
  }
}

function onBlur() {
  reportSwitchScreen()
}

// 切屏上报：visibilitychange 与 blur 可能同时触发，1.5s 内去重
async function reportSwitchScreen() {
  if (submitted.value) return
  const now = Date.now()
  if (now - lastSwitchAt < 1500) return
  lastSwitchAt = now
  try {
    const res = await takeAPI.reportSwitch(examId)
    if (res?.autoSubmitted) {
      await handleServerForcedSubmit()
    } else if (res?.switchCount) {
      ElMessage.warning(`已记录切屏行为（第 ${res.switchCount} 次，达 3 次将强制交卷）`)
    }
  } catch (e) { /* 上报失败静默 */ }
}

// 服务端强制交卷后同步本地状态并展示成绩
async function handleServerForcedSubmit() {
  submitted.value = true
  if (timer) clearInterval(timer)
  cleanupListeners()
  ElMessage.warning('切屏次数达到上限，系统已强制交卷')
  try {
    const report = await takeAPI.report(examId)
    result.value = {
      score: report.score ?? 0,
      totalScore: report.totalScore ?? 0,
      grading: !!report.grading,
      hasSubjective: (report.answers || []).some(a => a.needsManualGrade)
    }
  } catch (e) { /* 成绩稍后可在“我的成绩”查看 */ }
}

function isSelected(q, optLetter) {
  const val = answers[q.questionId]
  if (!val) return false
  if (q.type === 'single_choice' || q.type === 'true_false') return val === optLetter
  return val.split(',').map(s => s.trim()).includes(optLetter)
}

function selectOption(q, optLetter) {
  if (q.type === 'multiple_choice') {
    const cur = answers[q.questionId] ? answers[q.questionId].split(',').map(s => s.trim()) : []
    if (cur.includes(optLetter)) {
      answers[q.questionId] = cur.filter(x => x !== optLetter).join(',')
    } else {
      answers[q.questionId] = [...cur, optLetter].join(',')
    }
  } else {
    answers[q.questionId] = optLetter
  }
  saveAnswer(q.questionId)
}

async function saveAnswer(questionId) {
  try {
    await takeAPI.saveAnswer(examId, questionId, answers[questionId] || '')
  } catch (e) { /* 自动保存失败静默，交卷前仍有本地答案 */ }
}

async function submitExam(auto = false) {
  if (!auto) {
    try {
      await ElMessageBox.confirm('确认提交试卷？提交后无法修改。', '提示', { type: 'warning' })
    } catch (e) { return }
  }
  try {
    const data = await takeAPI.submit(examId)
    result.value = {
      score: data.score ?? 0,
      totalScore: data.totalScore ?? 0,
      grading: !!data.grading,
      hasSubjective: false
    }
    submitted.value = true
    if (timer) clearInterval(timer)
    cleanupListeners()
    // 拉取报告确认异步判分状态与是否含待人工评阅题
    try {
      const report = await takeAPI.report(examId)
      result.value.grading = !!report.grading
      result.value.hasSubjective = (report.answers || []).some(a => a.needsManualGrade)
    } catch (e) { /* 报告稍后可在“我的成绩”查看 */ }
    ElMessage.success(data.grading ? '试卷已提交，主观题 AI 评分中' : '试卷已提交')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '提交失败')
  }
}

function goResults() { router.push('/my-results') }
function formatTime(sec) {
  const m = Math.floor(sec / 60).toString().padStart(2, '0')
  const s = (sec % 60).toString().padStart(2, '0')
  return m + ':' + s
}
function typeLabel(t) {
  return { single_choice: '单选', multiple_choice: '多选', true_false: '判断',
    fill_blank: '填空', short_answer: '简答', programming: '编程题',
    calculation: '计算题', application: '应用题' }[t] || t
}
function diffLabel(d) { return { easy: '简单', medium: '中等', hard: '困难' }[d] || d }
</script>

<style scoped>
/* .chip 复用全局主题感知样式（自动适配亮暗） */
.options > div {
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 6px;
  margin-bottom: 8px;
  cursor: pointer;
  background: var(--surface-2);
  color: var(--ink-2);
}
.options > div.selected {
  border-color: var(--el-color-primary);
  background: var(--wash-blue);
  color: var(--el-color-primary-dark-2);
}
textarea {
  width: 100%;
  min-height: 120px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  padding: 10px;
}
</style>
