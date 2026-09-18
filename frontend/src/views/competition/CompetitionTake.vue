<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>限时答题</h2>
      <div>
        <el-tag :type="remaining <= 60 ? 'danger' : 'warning'" size="large">⏱ {{ countdown }}</el-tag>
        <el-button type="primary" style="margin-left:12px;" :loading="submitting" @click="finish">交卷</el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :span="16">
        <el-card v-for="(q, idx) in questions" :key="q.cqId" style="margin-bottom:12px;">
          <div style="font-weight:600;">{{ idx + 1 }}. {{ q.content }}</div>
          <div style="margin:10px 0;">
            <template v-if="q.qType === 'single_choice' || q.qType === 'true_false'">
              <el-radio-group v-model="answers[q.cqId]" @change="() => submitAnswer(q)">
                <div v-for="(opt, i) in (q.options || [])" :key="i">
                  <el-radio :value="opt">{{ opt }}</el-radio>
                </div>
              </el-radio-group>
            </template>
            <template v-else-if="q.qType === 'multiple_choice'">
              <el-checkbox-group v-model="multi[q.cqId]" @change="() => submitMulti(q)">
                <div v-for="(opt, i) in (q.options || [])" :key="i"><el-checkbox :value="opt">{{ opt }}</el-checkbox></div>
              </el-checkbox-group>
            </template>
            <template v-else>
              <el-input v-model="answers[q.cqId]" placeholder="填写答案后回车提交" @keyup.enter="submitAnswer(q)" style="max-width:360px;" />
            </template>
          </div>
          <el-alert v-if="results[q.cqId]" :type="results[q.cqId].isCorrect ? 'success' : 'error'" :closable="false" show-icon
                    :title="results[q.cqId].isCorrect ? `答对 +${results[q.cqId].gainedScore}` : '答错'"
                    :description="'正确答案：' + results[q.cqId].correctAnswer" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header><strong>实时榜单 Top10</strong></template>
          <el-table :data="board" size="small">
            <el-table-column prop="rank" label="#" width="45" />
            <el-table-column prop="username" label="选手" show-overflow-tooltip />
            <el-table-column prop="score" label="得分" width="70" />
          </el-table>
          <div style="margin-top:8px;color:#909399;font-size:12px;">当前得分：{{ myScore }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { competitionAPI } from '../../api/endpoints'
import { subscribe, connectSocket } from '../../utils/socket'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const questions = ref([])
const answers = reactive({})
const multi = reactive({})
const results = reactive({})
const myScore = ref(0)
const deadline = ref(null)
const remaining = ref(0)
const board = ref([])
let timer = null
let unsub = null

const countdown = computed(() => {
  const m = Math.floor(remaining.value / 60)
  const s = remaining.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})

async function start() {
  loading.value = true
  try {
    let res
    try { res = await competitionAPI.start(id) }
    catch (e) { res = await competitionAPI.play(id) }
    const qs = res.questions || []
    questions.value = qs
    qs.forEach(q => { if (q.qType === 'multiple_choice') multi[q.cqId] = [] })
    deadline.value = res.deadline
    myScore.value = res.score || 0
    if (res.answers) {
      res.answers.forEach(a => {
        results[a.cqId] = { isCorrect: a.isCorrect, gainedScore: a.gainedScore, correctAnswer: '' }
        if (a.answer) {
          if (Array.isArray(answers[a.cqId])) answers[a.cqId] = a.answer.split(',')
          else answers[a.cqId] = a.answer
        }
      })
    }
    remaining.value = res.remainingSec || Math.max(0, secondsTo(deadline.value))
    tick()
  } catch (e) { /* noop */ } finally { loading.value = false }
}

function secondsTo(dl) { return dl ? Math.max(0, Math.floor((new Date(dl).getTime() - Date.now()) / 1000)) : 0 }

function tick() {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    remaining.value = Math.max(0, remaining.value - 1)
    if (remaining.value <= 0) { clearInterval(timer); finish(true) }
  }, 1000)
}

async function submitAnswer(q) {
  const val = answers[q.cqId]
  if (val === undefined || val === null || val === '') return
  await send(q.cqId, val)
}
async function submitMulti(q) {
  const arr = multi[q.cqId] || []
  if (!arr.length) return
  await send(q.cqId, arr.join(','))
}

async function send(cqId, answer) {
  try {
    const r = await competitionAPI.answer(id, { cqId, answer })
    results[cqId] = { isCorrect: r.isCorrect, gainedScore: r.gainedScore, correctAnswer: r.correctAnswer }
    myScore.value = r.totalScore
  } catch (e) { /* noop */ }
}

async function loadBoard() {
  try { const lb = await competitionAPI.leaderboard(id); board.value = (lb.entries || []).slice(0, 10) }
  catch (e) { /* noop */ }
}

async function finish(auto) {
  if (submitting.value) return
  submitting.value = true
  try {
    const r = await competitionAPI.finish(id)
    ElMessage.success(auto ? '已超时自动交卷' : `交卷成功，得分 ${r.score}，名次 ${r.rank}/${r.total}`)
    router.push(`/competition/${id}/leaderboard`)
  } catch (e) { /* noop */ } finally { submitting.value = false }
}

onMounted(() => {
  connectSocket()
  start()
  loadBoard()
  unsub = subscribe(`/topic/competition/${id}`, () => loadBoard())
  // 轮询兜底
  const poll = setInterval(loadBoard, 15000)
  onBeforeUnmountClear.push(() => clearInterval(poll))
})

const onBeforeUnmountClear = []
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (unsub) unsub()
  onBeforeUnmountClear.forEach(f => f())
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
