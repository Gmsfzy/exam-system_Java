<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>1v1 对战</h2>
      <div v-if="battle">
        <el-tag :type="battle.status === 'playing' ? 'success' : 'info'">{{ statusText(battle.status) }}</el-tag>
        <el-tag v-if="battle.status === 'playing'" style="margin-left:8px;" :type="remaining <= 60 ? 'danger' : 'warning'">⏱ {{ countdown }}</el-tag>
        <el-button type="primary" style="margin-left:8px;" :disabled="battle.status !== 'playing'" @click="finish">完成作答</el-button>
      </div>
    </div>

    <el-alert v-if="battle && battle.status === 'waiting'" type="info" :closable="false" show-icon
              title="挑战已发起，等待对手应战…" style="margin-bottom:12px;" />
    <el-alert v-if="finished" :type="resultType" :closable="false" show-icon :title="resultTitle" style="margin-bottom:12px;" />

    <el-row :gutter="16" v-if="battle">
      <el-col :span="16">
        <el-card v-for="(q, idx) in questions" :key="q.cqId" style="margin-bottom:12px;">
          <div style="font-weight:600;">{{ idx + 1 }}. {{ q.content }}</div>
          <div style="margin:10px 0;">
            <template v-if="q.qType === 'single_choice' || q.qType === 'true_false'">
              <el-radio-group v-model="answers[q.cqId]" :disabled="answered[q.cqId]" @change="() => send(q)">
                <div v-for="(opt,i) in (q.options||[])" :key="i"><el-radio :value="opt">{{ opt }}</el-radio></div>
              </el-radio-group>
            </template>
            <template v-else-if="q.qType === 'multiple_choice'">
              <el-checkbox-group v-model="multi[q.cqId]" :disabled="answered[q.cqId]" @change="() => sendMulti(q)">
                <div v-for="(opt,i) in (q.options||[])" :key="i"><el-checkbox :value="opt">{{ opt }}</el-checkbox></div>
              </el-checkbox-group>
            </template>
            <template v-else>
              <el-input v-model="answers[q.cqId]" :disabled="answered[q.cqId]" placeholder="填写答案后回车" @keyup.enter="send(q)" style="max-width:360px;" />
            </template>
          </div>
          <el-alert v-if="results[q.cqId]" :type="results[q.cqId].isCorrect ? 'success':'error'" :closable="false"
                    :title="results[q.cqId].isCorrect ? `答对 +${results[q.cqId].gainedScore}` : '答错'"
                    :description="'正确答案：'+results[q.cqId].correctAnswer" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header><strong>实时对战</strong></template>
          <div style="text-align:center;font-size:28px;font-weight:700;margin:8px 0;">{{ myScore }} : {{ oppScore }}</div>
          <div style="display:flex;justify-content:space-between;color:#909399;font-size:13px;">
            <span>{{ meName }}</span><span>{{ oppName }}</span>
          </div>
          <el-divider />
          <div style="font-size:13px;color:#606266;">
            我方已答：{{ myAnswered }} / {{ questions.length }}<br />
            对手已答：{{ oppAnswered }} / {{ questions.length }}
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { pkAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'
import { subscribe, connectSocket } from '../../utils/socket'

const route = useRoute()
const auth = useAuthStore()
const battleId = Number(route.params.battleId)

const loading = ref(false)
const battle = ref(null)
const questions = ref([])
const answers = reactive({})
const multi = reactive({})
const results = reactive({})
const answered = reactive({})
const remaining = ref(0)
let timer = null, unsub = null, poll = null

const isChallenger = computed(() => battle.value && battle.value.challengerId === auth.currentUser?.id)
const myScore = computed(() => battle.value ? (isChallenger.value ? battle.value.challengerScore : battle.value.opponentScore) : 0)
const oppScore = computed(() => battle.value ? (isChallenger.value ? battle.value.opponentScore : battle.value.challengerScore) : 0)
const myAnswered = computed(() => battle.value ? (isChallenger.value ? battle.value.challengerAnswered : battle.value.opponentAnswered) : 0)
const oppAnswered = computed(() => battle.value ? (isChallenger.value ? battle.value.opponentAnswered : battle.value.challengerAnswered) : 0)
const meName = computed(() => battle.value ? (isChallenger.value ? battle.value.challengerName : battle.value.opponentName) : '')
const oppName = computed(() => battle.value ? (isChallenger.value ? battle.value.opponentName : battle.value.challengerName) : '')
const finished = computed(() => battle.value && battle.value.status === 'finished')
const countdown = computed(() => { const m = Math.floor(remaining.value / 60); const s = remaining.value % 60; return `${m}:${String(s).padStart(2,'0')}` })
const resultType = computed(() => {
  if (!finished.value) return 'info'
  if (battle.value.winnerId == null) return 'info'
  return battle.value.winnerId === auth.currentUser?.id ? 'success' : 'error'
})
const resultTitle = computed(() => {
  if (!finished.value) return ''
  if (battle.value.winnerId == null) return '平局！'
  return battle.value.winnerId === auth.currentUser?.id ? '🎉 你赢了！' : '惜败，再接再厉'
})

function statusText(s) { return { waiting: '等待应战', playing: '进行中', finished: '已结束', cancelled: '已取消' }[s] || s }

async function load() {
  try {
    const st = await pkAPI.state(battleId)
    battle.value = st.battle
    questions.value = st.questions || []
    remaining.value = st.remainingSec || 0
    questions.value.forEach(q => { if (multi[q.cqId] === undefined) multi[q.cqId] = [] })
    if (st.myAnswers) st.myAnswers.forEach(a => { answered[a.cqId] = true; results[a.cqId] = { isCorrect: a.isCorrect, gainedScore: a.gainedScore, correctAnswer: '' }; if (a.answer) answers[a.cqId] = a.answer })
    if (battle.value.status === 'playing' && remaining.value > 0 && !timer) tick()
  } catch (e) { /* noop */ }
}

function tick() {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    remaining.value = Math.max(0, remaining.value - 1)
    if (remaining.value <= 0) { clearInterval(timer); timer = null; load() }
  }, 1000)
}

async function send(q) { const v = answers[q.cqId]; if (v == null || v === '') return; await doAnswer(q.cqId, v) }
async function sendMulti(q) { const arr = multi[q.cqId] || []; if (!arr.length) return; await doAnswer(q.cqId, arr.join(',')) }
async function doAnswer(cqId, answer) {
  loading.value = true
  try {
    const r = await pkAPI.answer(battleId, { cqId, answer })
    results[cqId] = { isCorrect: r.isCorrect, gainedScore: r.gainedScore, correctAnswer: r.correctAnswer }
    answered[cqId] = true
    load()
  } catch (e) { /* noop */ } finally { loading.value = false }
}

async function finish() {
  try { await pkAPI.finish(battleId); ElMessage.success('已提交，等待结算'); load() } catch (e) { /* noop */ }
}

onMounted(() => {
  connectSocket()
  load()
  unsub = subscribe(`/topic/battle/${battleId}`, () => load())
  poll = setInterval(load, 8000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  if (poll) clearInterval(poll)
  if (unsub) unsub()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
