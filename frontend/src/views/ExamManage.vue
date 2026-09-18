<template>
  <div v-if="exam">
    <div class="page-header">
      <h2>考试管理 - {{ exam.title }}</h2>
      <div style="display: flex; gap: 8px;">
        <el-button size="small" type="success" :disabled="exam.status !== 'draft'" @click="publish">发布考试</el-button>
        <el-button size="small" type="warning" :disabled="exam.status === 'ended'" @click="end">结束考试</el-button>
        <el-button @click="router.back()">返回</el-button>
      </div>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="考试名称">{{ exam.title }}</el-descriptions-item>
          <el-descriptions-item label="时长">{{ exam.duration }} 分钟</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(exam.status)">{{ statusLabel(exam.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ exam.createdAt || '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ exam.startTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ exam.endTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ exam.description || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邀请码" :span="2">
            <span v-if="exam.invitationCode" style="font-weight: 600; letter-spacing: 2px;">{{ exam.invitationCode }}</span>
            <span v-else style="color: #909399;">尚未生成</span>
            <el-button size="small" style="margin-left: 12px;" @click="generateCode">
              {{ exam.invitationCode ? '重新生成' : '生成邀请码' }}
            </el-button>
            <el-button size="small" type="primary" @click="goGrading">阅卷 / 成绩</el-button>
            <el-button size="small" @click="goAnalysis">成绩分析</el-button>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <el-tab-pane :label="`题目 (${examQuestions.length})`" name="questions">
        <div style="display: flex; justify-content: space-between; margin-bottom: 16px; align-items: center;">
          <span>已添加 {{ examQuestions.length }} 题，总分 {{ totalScore }}（每题默认 10 分）</span>
          <el-button type="primary" @click="openPickQ">+ 添加题目</el-button>
        </div>
        <el-table :data="examQuestions" stripe>
          <el-table-column prop="order" label="序号" width="70" />
          <el-table-column prop="content" label="题目" min-width="320" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }"><el-tag size="small">{{ typeLabel(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="难度" width="90">
            <template #default="{ row }">{{ diffLabel(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="分值" width="80" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeQ(row.questionId)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane :label="`考生 (${examStudents.length})`" name="students">
        <div style="display: flex; justify-content: space-between; margin-bottom: 16px; align-items: center;">
          <span>已邀请 {{ examStudents.length }} 人</span>
          <el-button type="primary" @click="openPickS">+ 邀请学生</el-button>
        </div>
        <el-table :data="examStudents" stripe>
          <el-table-column prop="id" label="ID" width="100" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="email" label="邮箱" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeS(row.id)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showPickQ" title="选择题目（可多选批量添加）" width="820px">
      <div style="display: flex; gap: 8px; margin-bottom: 12px;">
        <el-select v-model="qFilter.majorId" placeholder="学科" clearable style="width: 160px;">
          <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
        <el-select v-model="qFilter.type" placeholder="类型" clearable style="width: 140px;">
          <el-option v-for="t in TYPE_OPTIONS" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="qFilter.difficulty" placeholder="难度" clearable style="width: 120px;">
          <el-option label="简单" value="easy" />
          <el-option label="中等" value="medium" />
          <el-option label="困难" value="hard" />
        </el-select>
        <el-button @click="loadQuestionPool">查询</el-button>
      </div>
      <el-table :data="availableQuestions" max-height="380" stripe @selection-change="pickQ = $event">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="content" label="题目" min-width="300" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }"><el-tag size="small">{{ typeLabel(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="难度" width="90">
          <template #default="{ row }">{{ diffLabel(row.difficulty) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showPickQ = false">取消</el-button>
        <el-button type="primary" :disabled="!pickQ.length" @click="confirmPickQ">
          添加选中的 {{ pickQ.length }} 题
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showPickS" title="邀请学生（可多选）" width="560px">
      <el-table :data="availableStudents" max-height="380" stripe @selection-change="pickS = $event">
        <el-table-column type="selection" width="50" />
        <el-table-column prop="id" label="ID" width="100" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="email" label="邮箱" />
      </el-table>
      <template #footer>
        <el-button @click="showPickS = false">取消</el-button>
        <el-button type="primary" :disabled="!pickS.length" @click="confirmPickS">
          邀请选中的 {{ pickS.length }} 人
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examAPI, questionAPI, majorAPI, studentAPI } from '../api/endpoints'

const TYPE_OPTIONS = [
  { value: 'single_choice', label: '单选题' },
  { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' },
  { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' },
  { value: 'programming', label: '编程题' },
  { value: 'calculation', label: '计算题' },
  { value: 'application', label: '应用题' }
]

const router = useRouter()
const route = useRoute()
const examId = route.params.id

const exam = ref(null)
const tab = ref('questions')
const majors = ref([])
const allStudents = ref([])
const questionPool = ref([])
const examQuestions = ref([])
const examStudents = ref([])

const qFilter = reactive({ majorId: null, type: '', difficulty: '' })
const showPickQ = ref(false)
const showPickS = ref(false)
const pickQ = ref([])
const pickS = ref([])

const totalScore = computed(() => examQuestions.value.reduce((s, q) => s + (q.score || 0), 0))
const addedIds = computed(() => new Set(examQuestions.value.map(q => q.questionId)))
const invitedIds = computed(() => new Set(examStudents.value.map(s => s.id)))
const availableQuestions = computed(() => questionPool.value.filter(q => !addedIds.value.has(q.id)))
const availableStudents = computed(() => allStudents.value.filter(s => !invitedIds.value.has(s.id)))

onMounted(loadAll)

async function loadAll() {
  try {
    const [detail, questions, students, majorList, studentList] = await Promise.all([
      examAPI.get(examId),
      examAPI.examQuestions(examId).catch(() => []),
      examAPI.examStudents(examId).catch(() => []),
      majorAPI.list().catch(() => []),
      studentAPI.list().catch(() => [])
    ])
    exam.value = detail
    examQuestions.value = questions || []
    examStudents.value = students || []
    majors.value = majorList || []
    allStudents.value = studentList || []
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '考试加载失败')
  }
}

async function loadQuestionPool() {
  try {
    const params = {}
    if (qFilter.majorId) params.majorId = qFilter.majorId
    if (qFilter.type) params.type = qFilter.type
    if (qFilter.difficulty) params.difficulty = qFilter.difficulty
    questionPool.value = await questionAPI.list(params) || []
  } catch (e) {
    ElMessage.error('题库加载失败')
  }
}

function openPickQ() {
  pickQ.value = []
  showPickQ.value = true
  loadQuestionPool()
}

function openPickS() {
  pickS.value = []
  showPickS.value = true
}

async function confirmPickQ() {
  const ids = pickQ.value.map(q => q.id)
  if (!ids.length) return
  try {
    await examAPI.addQuestions(examId, ids)
    ElMessage.success(`已添加 ${ids.length} 题`)
    showPickQ.value = false
    await refreshExamQuestions()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '添加失败')
  }
}

async function removeQ(questionId) {
  try {
    await examAPI.removeQuestion(examId, questionId)
    ElMessage.success('已移除')
    await refreshExamQuestions()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '移除失败')
  }
}

async function confirmPickS() {
  const ids = pickS.value.map(s => s.id)
  if (!ids.length) return
  try {
    await examAPI.invite(examId, ids)
    ElMessage.success(`已邀请 ${ids.length} 人`)
    showPickS.value = false
    await refreshExamStudents()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '邀请失败')
  }
}

async function removeS(studentId) {
  try {
    await examAPI.removeStudent(examId, studentId)
    ElMessage.success('已移除')
    await refreshExamStudents()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '移除失败')
  }
}

async function generateCode() {
  try {
    const res = await examAPI.invitation(examId)
    exam.value.invitationCode = res.code
    exam.value.invitationUrl = res.url
    ElMessage.success('邀请码：' + res.code)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '生成失败')
  }
}

async function publish() {
  try {
    await ElMessageBox.confirm('确认发布该考试？发布后学生可参加。', '提示', { type: 'warning' })
    exam.value = await examAPI.publish(examId)
    ElMessage.success('已发布')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '发布失败')
  }
}

async function end() {
  try {
    await ElMessageBox.confirm('确认结束该考试？', '提示', { type: 'warning' })
    exam.value = await examAPI.end(examId)
    ElMessage.success('已结束')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function refreshExamQuestions() {
  examQuestions.value = await examAPI.examQuestions(examId).catch(() => [])
}
async function refreshExamStudents() {
  examStudents.value = await examAPI.examStudents(examId).catch(() => [])
}

function goGrading() { router.push({ path: '/grading', query: { examId } }) }
function goAnalysis() { router.push({ path: '/results', query: { examId } }) }
function statusLabel(s) { return { draft: '草稿', published: '已发布', ended: '已结束' }[s] || s }
function statusType(s) { return { draft: 'info', published: 'success', ended: 'danger' }[s] || '' }
function typeLabel(t) {
  return (TYPE_OPTIONS.find(o => o.value === t)?.label) || t
}
function diffLabel(d) { return { easy: '简单', medium: '中等', hard: '困难' }[d] || d }
</script>
