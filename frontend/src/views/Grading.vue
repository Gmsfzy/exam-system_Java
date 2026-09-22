<template>
  <div>
    <div class="page-header">
      <h2>人工阅卷</h2>
      <div style="display: flex; gap: 12px; align-items: center;">
        <el-radio-group v-model="mode" @change="onModeChange">
          <el-radio-button value="student">逐人批注</el-radio-button>
          <el-radio-button value="cluster">聚类批注</el-radio-button>
        </el-radio-group>
        <el-select v-model="examId" placeholder="选择考试" style="width: 260px;" @change="loadStudents">
          <el-option v-for="e in exams" :key="e.id" :label="e.title" :value="e.id" />
        </el-select>
      </div>
    </div>

    <template v-if="mode === 'student'">
    <el-empty v-if="!examId" description="请先选择一场考试" />
    <el-empty v-else-if="students.length === 0" description="该考试暂无待处理试卷" />
    <el-table v-else :data="students" stripe>
      <el-table-column prop="studentId" label="学生ID" width="90" />
      <el-table-column prop="studentName" label="学生" width="160" />
      <el-table-column label="轮次" width="80">
        <template #default="{ row }">第 {{ row.attemptNo || 1 }} 次</template>
      </el-table-column>
      <el-table-column prop="examTitle" label="考试" min-width="200" />
      <el-table-column label="当前得分" width="160">
        <template #default="{ row }"><strong>{{ row.score }} / {{ row.totalScore }}</strong></template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.hasPendingManual" type="warning">待评阅</el-tag>
          <el-tag v-else type="success">已完成</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click="openGrade(row)">评阅</el-button>
        </template>
      </el-table-column>
    </el-table>
    </template>

    <template v-else>
      <el-empty v-if="!examId" description="请先选择一场考试" />
      <el-empty v-else-if="clusters.length === 0" description="该考试暂无待批注的主观题" />
      <div v-else>
        <div v-for="c in clusters" :key="c.questionId" class="answer-card">
          <div style="margin-bottom: 8px;">
            <el-tag size="small" style="margin-right: 8px;">{{ typeLabel(c.type) }}</el-tag>
            <strong>{{ c.content }}</strong>
            <span style="float: right; color: #909399;">满分 {{ c.maxScore }} · 待批 {{ (c.answers || []).length }} 份</span>
          </div>
          <el-collapse>
            <el-collapse-item :title="`展开 ${ (c.answers || []).length } 份作答`">
              <div v-for="ans in (c.answers || [])" :key="ans.answerId" style="padding: 6px 0; border-bottom: 1px dashed var(--line);">
                <div style="font-size: 13px; color: #909399;">{{ ans.studentName }}</div>
                <div style="white-space: pre-wrap;">{{ ans.studentAnswer || '(未作答)' }}</div>
                <div v-if="ans.aiAnalysis" style="font-size: 12px; color: var(--el-color-primary);">🤖 AI参考：{{ ans.aiScore }} 分 — {{ ans.aiAnalysis }}</div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <div style="display: flex; align-items: center; gap: 10px; margin-top: 10px; flex-wrap: wrap;">
            <span><strong>本题统一给分：</strong></span>
            <el-input-number v-model="c._score" :min="0" :max="c.maxScore || 100" :step="1" size="small" />
            <el-input v-model="c._comment" placeholder="统一评语（可选）" size="small" style="flex: 1; min-width: 200px;" />
            <el-button size="small" type="primary" :loading="batchingId === c.questionId" @click="batchGrade(c)">
              批量给分（{{ (c.answers || []).length }} 份）
            </el-button>
          </div>
        </div>
      </div>
    </template>

    <el-dialog v-model="showGrade" :title="`评阅 - ${current?.studentName || ''}`" width="820px" top="5vh">
      <div v-if="answers.length === 0" style="text-align: center; padding: 30px; color: #909399;">暂无可评阅的作答</div>
      <div v-for="(a, idx) in answers" :key="a.answerId || idx" class="answer-card">
        <div style="margin-bottom: 8px;">
          <el-tag size="small" style="margin-right: 8px;">{{ typeLabel(a.type) }}</el-tag>
          <strong>第 {{ idx + 1 }} 题</strong>
          <span style="float: right; color: #909399;">满分 {{ maxScoreOf(a.questionId) }}</span>
        </div>
        <h4 style="margin: 8px 0;">{{ a.content }}</h4>
        <div style="padding: 8px 10px; background: var(--surface-2); border-radius: 4px; margin-bottom: 6px;">
          <strong>学生答案：</strong><span style="white-space: pre-wrap;">{{ a.studentAnswer || '(未作答)' }}</span>
        </div>
        <div style="padding: 8px 10px; background: var(--wash-blue); border-radius: 4px; margin-bottom: 6px;">
          <strong>参考答案：</strong><span style="white-space: pre-wrap;">{{ a.correctAnswer || '-' }}</span>
        </div>
        <div v-if="a.aiAnalysis" style="padding: 8px 10px; background: var(--wash-cyan); border-radius: 4px; margin-bottom: 6px; font-size: 13px;">
          🤖 AI 参考：{{ a.aiScore }} 分 —— {{ a.aiAnalysis }}
        </div>
        <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
          <span><strong>评分：</strong></span>
          <el-input-number v-model="a._score" :min="0" :max="maxScoreOf(a.questionId)" :step="1" size="small" />
          <el-input v-model="a._comment" placeholder="评语（可选）" size="small" style="flex: 1; min-width: 200px;" />
          <el-button size="small" type="primary" :loading="savingId === a.answerId" @click="saveOne(a)">保存</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resultAPI, examAPI } from '../api/endpoints'

const route = useRoute()
const exams = ref([])
const examId = ref(route.query.examId ? Number(route.query.examId) : null)
const students = ref([])
const showGrade = ref(false)
const current = ref(null)
const answers = ref([])
const savingId = ref(null)
const scoreMap = ref({})
// M6 聚类批注 / 同题批量给分
const mode = ref('student')
const clusters = ref([])
const batchingId = ref(null)

onMounted(async () => {
  try {
    exams.value = await examAPI.list() || []
    if (!examId.value && exams.value.length) examId.value = exams.value[0].id
    if (examId.value) await loadStudents()
  } catch (e) {}
})

async function loadStudents() {
  students.value = []
  clusters.value = []
  if (!examId.value) return
  if (mode.value === 'cluster') { await loadClusters(); return }
  try {
    const [list, qs] = await Promise.all([
      resultAPI.gradingList(examId.value).catch(() => []),
      examAPI.examQuestions(examId.value).catch(() => [])
    ])
    students.value = list || []
    scoreMap.value = {}
    for (const q of (qs || [])) scoreMap.value[q.questionId] = q.score
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载失败')
  }
}

function onModeChange() { loadStudents() }

async function loadClusters() {
  try {
    const list = await resultAPI.gradingCluster(examId.value).catch(() => [])
    clusters.value = (list || []).map(c => ({ ...c, _score: 0, _comment: '' }))
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '聚类加载失败')
  }
}

async function batchGrade(c) {
  batchingId.value = c.questionId
  try {
    const res = await resultAPI.batchGradeQuestion(examId.value, {
      questionId: c.questionId, score: c._score, comment: c._comment
    })
    ElMessage.success(`已为 ${res.gradedCount ?? 0} 份作答统一给分`)
    await loadClusters()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '批量给分失败')
  } finally {
    batchingId.value = null
  }
}

async function openGrade(row) {
  current.value = row
  answers.value = []
  showGrade.value = true
  try {
    const list = await resultAPI.gradingDetail(examId.value, row.studentId)
    answers.value = (list || []).map(a => ({
      ...a,
      _score: a.manualScore != null ? a.manualScore : (a.aiScore != null ? a.aiScore : 0),
      _comment: a.manualComment || ''
    }))
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '作答加载失败')
  }
}

function maxScoreOf(questionId) {
  return scoreMap.value[questionId] ?? 100
}

async function saveOne(a) {
  savingId.value = a.answerId
  try {
    await resultAPI.manualGrade(examId.value, current.value.studentId, {
      questionId: a.questionId,
      manualScore: a._score,
      manualComment: a._comment
    })
    a.manualScore = a._score
    a.manualComment = a._comment
    ElMessage.success('评分已保存')
    loadStudents()
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
.answer-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 14px;
}
</style>
