<template>
  <div>
    <div class="page-header"><h2>竞赛管理</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建竞赛</el-button>
    </div>

    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }"><el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="时间窗" width="230">
        <template #default="{ row }">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</template>
      </el-table-column>
      <el-table-column prop="duration" label="时长(分)" width="90" />
      <el-table-column prop="drawCount" label="抽题" width="70" />
      <el-table-column prop="totalScore" label="总分" width="70" />
      <el-table-column prop="participantCount" label="参与" width="70" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">管理</el-button>
          <el-button size="small" type="success" :disabled="row.status !== 'draft'" @click="publish(row)">发布</el-button>
          <el-button size="small" type="warning" :disabled="row.status === 'ended'" @click="end(row)">结束</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建 / 编辑 -->
    <el-dialog v-model="createVisible" :title="form.id ? '编辑竞赛' : '新建竞赛'" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
        <el-form-item label="答题时长"><el-input-number v-model="form.duration" :min="1" :max="180" /> 分钟</el-form-item>
        <el-form-item label="抽题数"><el-input-number v-model="form.drawCount" :min="0" /> （0=全部）</el-form-item>
        <el-form-item label="开放 PK"><el-switch v-model="form.allowPk" /></el-form-item>
        <el-form-item label="计分基准"><el-input-number v-model="form.baseRatio" :min="0" :max="1" :step="0.1" :precision="1" />
          <span style="margin:0 6px;">/</span>
          <el-input-number :model-value="speedRatio" disabled :step="0.1" :precision="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情：题目 + 参与者 -->
    <el-drawer v-model="detailVisible" :title="current?.title || '竞赛详情'" size="60%">
      <el-tabs v-if="current">
        <el-tab-pane label="题目">
          <div style="margin-bottom:12px;">
            <el-button type="primary" size="small" :disabled="current.status !== 'draft'" @click="openPicker">从题库添加</el-button>
            <span style="margin-left:12px;color:#909399;">共 {{ questions.length }} 题（仅客观题）</span>
          </div>
          <el-table :data="questions" border size="small">
            <el-table-column type="index" width="45" />
            <el-table-column prop="qContent" label="题干" min-width="220" show-overflow-tooltip />
            <el-table-column prop="qType" label="题型" width="110" />
            <el-table-column prop="answer" label="答案" width="120" />
            <el-table-column prop="score" label="分值" width="70" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button size="small" type="danger" link :disabled="current.status !== 'draft'" @click="removeQuestion(row)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="参与者">
          <el-table :data="participants" border size="small">
            <el-table-column type="index" width="45" />
            <el-table-column prop="username" label="用户" />
            <el-table-column prop="status" label="状态" width="100" />
            <el-table-column prop="score" label="得分" width="90" />
            <el-table-column prop="usedTime" label="用时(秒)" width="100" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 题库选题 -->
    <el-dialog v-model="pickerVisible" title="从题库选择题目" width="720px">
      <el-table :data="bank" @selection-change="s => picked = s" size="small" v-loading="bankLoading" max-height="420">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="content" label="题干" show-overflow-tooltip />
        <el-table-column prop="type" label="题型" width="120" />
        <el-table-column prop="difficulty" label="难度" width="90" />
      </el-table>
      <template #footer>
        <el-button @click="pickerVisible=false">取消</el-button>
        <el-button type="primary" @click="confirmPick">添加所选</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { competitionAdminAPI, questionAPI } from '../../api/endpoints'

const list = ref([])
const loading = ref(false)
const createVisible = ref(false)
const saving = ref(false)
const form = reactive({ id: null, title: '', description: '', startTime: null, endTime: null, duration: 15, drawCount: 0, allowPk: false, baseRatio: 0.7 })
const speedRatio = computed(() => Math.round((1 - form.baseRatio) * 10) / 10)

const detailVisible = ref(false)
const current = ref(null)
const questions = ref([])
const participants = ref([])

const pickerVisible = ref(false)
const bank = ref([])
const bankLoading = ref(false)
const picked = ref([])

function statusType(s) { return { published: 'info', ongoing: 'success', ended: 'info', draft: 'warning' }[s] || 'info' }
function statusText(s) { return { published: '未开始', ongoing: '进行中', ended: '已结束', draft: '草稿' }[s] || s }
function fmt(t) { return t ? t.replace('T', ' ').slice(0, 16) : '—' }

async function load() {
  loading.value = true
  try { list.value = await competitionAdminAPI.list() || [] }
  catch (e) { /* noop */ } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, { id: null, title: '', description: '', startTime: null, endTime: null, duration: 15, drawCount: 0, allowPk: false, baseRatio: 0.7 })
  createVisible.value = true
}

async function save() {
  if (!form.title) { ElMessage.warning('请输入标题'); return }
  const payload = {
    title: form.title, description: form.description, startTime: form.startTime, endTime: form.endTime,
    duration: form.duration, drawCount: form.drawCount, allowPk: form.allowPk,
    scoringRule: { base_ratio: form.baseRatio, speed_ratio: speedRatio.value }
  }
  saving.value = true
  try {
    if (form.id) await competitionAdminAPI.update(form.id, payload)
    else await competitionAdminAPI.create(payload)
    ElMessage.success('已保存')
    createVisible.value = false
    load()
  } catch (e) { /* noop */ } finally { saving.value = false }
}

async function openDetail(row) {
  detailVisible.value = true
  current.value = row
  try {
    const d = await competitionAdminAPI.detail(row.id)
    current.value = d.competition
    questions.value = d.questions || []
    participants.value = await competitionAdminAPI.participants(row.id) || []
  } catch (e) { /* noop */ }
}

async function publish(row) {
  try { await competitionAdminAPI.publish(row.id); ElMessage.success('已发布'); load() } catch (e) { /* noop */ }
}
async function end(row) {
  try { await competitionAdminAPI.end(row.id); ElMessage.success('已结束并结算'); load() } catch (e) { /* noop */ }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除竞赛「${row.title}」？`, '提示', { type: 'warning' })
    await competitionAdminAPI.remove(row.id); ElMessage.success('已删除'); load()
  } catch (e) { /* noop */ }
}

async function openPicker() {
  pickerVisible.value = true
  bankLoading.value = true
  try {
    const all = await questionAPI.list({ size: 200 })
    bank.value = all.filter(q => ['single_choice', 'multiple_choice', 'fill_blank', 'true_false'].includes(q.type))
  } catch (e) { /* noop */ } finally { bankLoading.value = false }
}

async function confirmPick() {
  if (!picked.value.length) { ElMessage.warning('未选择题目'); return }
  try {
    await competitionAdminAPI.addQuestions(current.value.id, picked.value.map(q => q.id))
    ElMessage.success('已添加')
    pickerVisible.value = false
    openDetail(current.value)
  } catch (e) { /* noop */ }
}

async function removeQuestion(row) {
  try {
    await competitionAdminAPI.removeQuestion(current.value.id, row.id)
    openDetail(current.value)
  } catch (e) { /* noop */ }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
