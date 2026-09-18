<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>悬赏详情</h2>
      <el-button :icon="Refresh" circle @click="load" />
    </div>

    <el-card v-if="bounty" class="main">
      <div class="card-top">
        <el-tag :type="bounty.bountyType==='question'?'primary':'success'" size="small">{{ bounty.bountyType==='question'?'题目征集':'答案征集' }}</el-tag>
        <el-tag :type="statusTag(bounty.status)" size="small">{{ statusText(bounty.status) }}</el-tag>
        <span class="reward">💰 {{ bounty.rewardPoints }}</span>
      </div>
      <h3>{{ bounty.title }}</h3>
      <div class="desc">{{ bounty.description || '—' }}</div>
      <div class="meta">
        发布者：{{ bounty.publisherName }}
        <span v-if="bounty.majorName"> · 专业：{{ bounty.majorName }}</span>
        <span v-if="bounty.qType"> · 题型：{{ typeLabel(bounty.qType) }}</span>
        <span v-if="bounty.deadline"> · 截止：{{ fmt(bounty.deadline) }}</span>
      </div>

      <!-- 题目快照（答案征集） -->
      <el-card v-if="snapshot && snapshot.content" class="sub" shadow="never">
        <template #header><strong>目标题目</strong></template>
        <div>{{ snapshot.content }}</div>
        <ul v-if="snapshot.options && snapshot.options.length" style="margin:6px 0 0;padding-left:20px;">
          <li v-for="(o,i) in snapshot.options" :key="i">{{ o }}</li>
        </ul>
        <div v-if="snapshot.answer" style="color:#67c23a;margin-top:6px;">参考答案（仅你可见）：{{ snapshot.answer }}</div>
      </el-card>
      <!-- 关联题库题 -->
      <el-card v-if="bounty.targetQuestion" class="sub" shadow="never">
        <template #header><strong>关联题库题</strong></template>
        <div>{{ bounty.targetQuestion.content }}</div>
        <ul v-if="bounty.targetQuestion.options && bounty.targetQuestion.options.length" style="margin:6px 0 0;padding-left:20px;">
          <li v-for="(o,i) in bounty.targetQuestion.options" :key="i">{{ o }}</li>
        </ul>
        <div v-if="bounty.targetQuestion.answer" style="margin-top:6px;color:#909399;">答案：{{ bounty.targetQuestion.answer }}</div>
        <div v-if="bounty.targetQuestion.analysis" style="color:#909399;">解析：{{ bounty.targetQuestion.analysis }}</div>
      </el-card>

      <div class="actions">
        <el-button v-if="canSubmit" type="primary" @click="openSubmit">我要投稿</el-button>
        <el-tag v-else-if="bounty.mySubmission" :type="subStatusTag(bounty.mySubmission.status)">我的投稿：{{ subStatusText(bounty.mySubmission.status) }}</el-tag>
        <span v-if="bounty.isPublisher" style="color:#909399;font-size:13px;">（你是发布者，可在下方审核）</span>
      </div>
    </el-card>

    <!-- 投稿列表 -->
    <el-card v-if="bounty" style="margin-top:16px;">
      <template #header><strong>投稿列表（{{ subs.length }}）</strong></template>
      <el-empty v-if="!subs.length" :description="bounty.isPublisher ? '暂无投稿' : '暂无已采纳投稿'" />
      <div v-for="s in subs" :key="s.id" class="submission">
        <div class="sub-head">
          <span><strong>{{ s.submitterName }}</strong></span>
          <el-tag size="small" :type="subStatusTag(s.status)">{{ subStatusText(s.status) }}</el-tag>
          <span style="color:#c0c4cc;font-size:12px;margin-left:auto;">{{ fmt(s.createdAt) }}</span>
        </div>
        <div class="sub-body">
          <template v-if="bounty.bountyType === 'question'">
            <div><strong>题干：</strong>{{ s.qContent }}</div>
            <div v-if="s.qOptions && s.qOptions.length"><strong>选项：</strong>{{ s.qOptions.join(' / ') }}</div>
            <div><strong>答案：</strong>{{ s.qAnswer }}</div>
            <div v-if="s.qAnalysis"><strong>解析：</strong>{{ s.qAnalysis }}</div>
            <div v-if="s.qKnowledge" style="color:#909399;">知识点：{{ s.qKnowledge }} · {{ typeLabel(s.qType) }} / {{ diffLabel(s.qDifficulty) }}</div>
          </template>
          <template v-else>
            <div style="white-space:pre-wrap;">{{ s.content }}</div>
          </template>
        </div>
        <div v-if="s.reviewComment" class="review-cmt">审核意见：{{ s.reviewComment }}</div>
        <div v-if="bounty.isPublisher && s.status === 'pending' && bounty.status === 'open'" class="sub-actions">
          <el-button type="success" size="small" @click="accept(s)">采纳</el-button>
          <el-button type="danger" size="small" @click="openReject(s)">拒绝</el-button>
        </div>
      </div>
    </el-card>

    <!-- 投稿对话框 -->
    <el-dialog v-model="submitVisible" title="提交投稿" width="560px">
      <el-form :model="subForm" label-width="70px">
        <template v-if="bounty && bounty.bountyType === 'question'">
          <el-form-item label="题干"><el-input v-model="subForm.qContent" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="选项"><el-input v-model="subForm.optionsText" type="textarea" :rows="4" placeholder="每行一个选项（单选/多选/判断题填写）" /></el-form-item>
          <el-form-item label="答案"><el-input v-model="subForm.qAnswer" placeholder="如 A / 对 / 具体答案" /></el-form-item>
          <el-form-item label="解析"><el-input v-model="subForm.qAnalysis" type="textarea" :rows="2" /></el-form-item>
          <el-form-item label="题型">
            <el-select v-model="subForm.qType" clearable :placeholder="'缺省 ' + (bounty.qType ? typeLabel(bounty.qType) : '单选题')">
              <el-option v-for="t in qTypes" :key="t.value" :value="t.value" :label="t.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select v-model="subForm.qDifficulty" clearable placeholder="缺省中等">
              <el-option value="easy" label="简单" /><el-option value="medium" label="中等" /><el-option value="hard" label="困难" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点"><el-input v-model="subForm.qKnowledge" placeholder="逗号分隔" /></el-form-item>
        </template>
        <template v-else>
          <el-form-item label="答案"><el-input v-model="subForm.content" type="textarea" :rows="6" placeholder="请填写答案与解析正文" /></el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="submitVisible=false">取消</el-button>
        <el-button type="primary" @click="doSubmit">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectVisible" title="拒绝投稿" width="420px">
      <el-input v-model="rejectComment" type="textarea" :rows="3" placeholder="拒绝理由（可选）" />
      <template #footer>
        <el-button @click="rejectVisible=false">取消</el-button>
        <el-button type="danger" @click="doReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { bountyAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const auth = useAuthStore()
const id = Number(route.params.id)

const loading = ref(false)
const bounty = ref(null)
const subs = ref([])
const submitVisible = ref(false)
const rejectVisible = ref(false)
const rejectComment = ref('')
let rejectTarget = null
const subForm = reactive({ content: '', qContent: '', optionsText: '', qAnswer: '', qAnalysis: '', qType: '', qDifficulty: '', qKnowledge: '' })

const qTypes = [
  { value: 'single_choice', label: '单选题' }, { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' }, { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' }
]

const snapshot = computed(() => bounty.value ? bounty.value.snapshot : null)
const canSubmit = computed(() => bounty.value && bounty.value.status === 'open' && !bounty.value.isPublisher && !bounty.value.mySubmission)

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }
function statusText(s) { return { open: '征集中', closed: '已采纳', expired: '已过期' }[s] || s }
function statusTag(s) { return { open: 'warning', closed: 'success', expired: 'info' }[s] || '' }
function subStatusText(s) { return { pending: '待审核', accepted: '已采纳', rejected: '已拒绝' }[s] || s }
function subStatusTag(s) { return { pending: 'info', accepted: 'success', rejected: 'danger' }[s] || '' }
function typeLabel(v) { return ({ single_choice:'单选题', multiple_choice:'多选题', true_false:'判断题', fill_blank:'填空题', short_answer:'简答题', programming:'编程题' })[v] || v || '—' }
function diffLabel(v) { return ({ easy:'简单', medium:'中等', hard:'困难' })[v] || v || '—' }

async function load() {
  loading.value = true
  try {
    bounty.value = await bountyAPI.detail(id)
    subs.value = await bountyAPI.submissions(id) || []
  } catch (e) { /* noop */ } finally { loading.value = false }
}

function openSubmit() {
  Object.assign(subForm, { content: '', qContent: '', optionsText: '', qAnswer: '', qAnalysis: '', qType: '', qDifficulty: '', qKnowledge: '' })
  submitVisible.value = true
}
async function doSubmit() {
  const payload = {}
  if (bounty.value.bountyType === 'question') {
    if (!subForm.qContent || !subForm.qAnswer) { ElMessage.warning('请填写题干与答案'); return }
    Object.assign(payload, {
      qContent: subForm.qContent, qAnswer: subForm.qAnswer, qAnalysis: subForm.qAnalysis,
      qType: subForm.qType || null, qDifficulty: subForm.qDifficulty || null, qKnowledge: subForm.qKnowledge,
      qOptions: subForm.optionsText.split('\n').map(s => s.trim()).filter(Boolean)
    })
  } else {
    if (!subForm.content) { ElMessage.warning('请填写答案正文'); return }
    payload.content = subForm.content
  }
  try { await bountyAPI.submit(id, payload); ElMessage.success('投稿成功'); submitVisible.value = false; load() } catch (e) { /* noop */ }
}

function openReject(s) { rejectTarget = s; rejectComment.value = ''; rejectVisible.value = true }
async function doReject() {
  try { await bountyAPI.reject(rejectTarget.id, rejectComment.value); ElMessage.success('已拒绝'); rejectVisible.value = false; load() } catch (e) { /* noop */ }
}
async function accept(s) {
  try {
    await ElMessageBox.confirm('确认采纳该投稿？采纳后悬赏将关闭，不可撤销。', '采纳确认', { type: 'warning' })
    const r = await bountyAPI.accept(s.id)
    ElMessage.success(r.message || '采纳成功')
    load()
  } catch (e) { /* noop */ }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.card-top { display: flex; align-items: center; gap: 8px; }
.reward { margin-left: auto; color: #e6a23c; font-weight: 700; }
.main h3 { margin: 10px 0 4px; }
.desc { color: #606266; margin-bottom: 8px; }
.meta { color: #909399; font-size: 13px; }
.sub { margin-top: 12px; background: #fafafa; }
.actions { margin-top: 14px; display: flex; align-items: center; gap: 12px; }
.submission { border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; margin-bottom: 12px; }
.sub-head { display: flex; align-items: center; gap: 8px; }
.sub-body { margin-top: 8px; color: #303133; font-size: 14px; line-height: 1.7; }
.review-cmt { margin-top: 6px; color: #f56c6c; font-size: 13px; }
.sub-actions { margin-top: 10px; text-align: right; }
</style>
