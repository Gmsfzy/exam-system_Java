<template>
  <div>
    <div class="page-header">
      <h2>试题分析 - {{ title || '加载中' }}</h2>
      <div style="display: flex; gap: 8px;">
        <el-button size="small" :loading="inspecting" @click="aiInspect">🤖 AI 试卷质检</el-button>
        <el-button size="small" @click="load">刷新</el-button>
        <el-button size="small" @click="router.back()">返回</el-button>
      </div>
    </div>

    <el-alert v-if="!loading && !rows.length" type="info" :closable="false"
      title="暂无作答数据，发布或有学生交卷后再查看试题分析。" style="margin-bottom: 12px;" />

    <el-table :data="rows" stripe v-loading="loading">
      <el-table-column prop="order" label="#" width="55" />
      <el-table-column label="题目" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">{{ row.content }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }"><el-tag size="small">{{ typeLabel(row.type) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="得分率" width="150">
        <template #default="{ row }">
          <el-progress :percentage="pct(row.scoreRate)" :stroke-width="12" :color="rateColor(row.scoreRate)" />
        </template>
      </el-table-column>
      <el-table-column label="区分度" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="discType(row.discrimination)">{{ fmtNum(row.discrimination) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="answerCount" label="作答数" width="90" />
      <el-table-column label="干扰项选择率" min-width="240">
        <template #default="{ row }">
          <div class="dist">
            <el-tooltip v-for="d in (row.distractors || [])" :key="d.option"
              :content="`${d.option}: ${(d.rate * 100).toFixed(0)}%${d.isCorrect ? ' (正确项)' : ''}`" placement="top">
              <div class="dist-bar-wrap">
                <div class="dist-bar" :class="{ correct: d.isCorrect }"
                  :style="{ height: Math.max(3, d.rate * 40) + 'px' }"></div>
                <span class="dist-label">{{ d.option }}</span>
              </div>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showInspect" title="AI 试卷质检结果" width="640px">
      <el-empty v-if="!inspect" description="无结果" />
      <div v-else>
        <h4 style="color: #f56c6c;">⚠ 发现的问题（{{ (inspect.issues || []).length }}）</h4>
        <ul>
          <li v-for="(it, i) in (inspect.issues || [])" :key="'i' + i">{{ it }}</li>
          <li v-if="!(inspect.issues && inspect.issues.length)" style="color: #909399;">未发现明显问题</li>
        </ul>
        <h4 style="color: #67c23a; margin-top: 16px;">💡 改进建议（{{ (inspect.suggestions || []).length }}）</h4>
        <ul>
          <li v-for="(s, i) in (inspect.suggestions || [])" :key="'s' + i">{{ s }}</li>
          <li v-if="!(inspect.suggestions && inspect.suggestions.length)" style="color: #909399;">暂无建议</li>
        </ul>
      </div>
      <template #footer><el-button type="primary" @click="showInspect = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { examAPI } from '../api/endpoints'

const router = useRouter()
const route = useRoute()
const examId = route.params.id

const rows = ref([])
const title = ref('')
const loading = ref(false)
const inspecting = ref(false)
const inspect = ref(null)
const showInspect = ref(false)

onMounted(async () => {
  try {
    const exam = await examAPI.get(examId).catch(() => null)
    title.value = exam?.title || ''
  } catch (e) { /* ignore */ }
  load()
})

async function load() {
  loading.value = true
  try {
    rows.value = await examAPI.itemAnalysis(examId) || []
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '分析数据加载失败')
  } finally {
    loading.value = false
  }
}

async function aiInspect() {
  inspecting.value = true
  try {
    inspect.value = await examAPI.aiInspect(examId)
    showInspect.value = true
  } catch (e) {
    ElMessage.error(e.response?.data?.message || 'AI 质检失败（可能未配置 AI 或触发限流）')
  } finally {
    inspecting.value = false
  }
}

function pct(v) { return Math.round((v || 0) * 100) }
function fmtNum(v) { return v == null ? '-' : (v >= 0 ? '' : '') + Number(v).toFixed(2) }
function rateColor(v) {
  const p = pct(v)
  return p >= 80 ? '#67c23a' : p >= 50 ? '#e6a23c' : '#f56c6c'
}
function discType(v) {
  if (v == null) return 'info'
  if (v >= 0.3) return 'success'
  if (v >= 0.15) return 'warning'
  return 'danger'
}
function typeLabel(t) {
  return { single_choice: '单选', multiple_choice: '多选', true_false: '判断', fill_blank: '填空',
    short_answer: '简答', programming: '编程题', calculation: '计算题', application: '应用题' }[t] || t
}
</script>

<style scoped>
.dist { display: flex; align-items: flex-end; gap: 10px; height: 48px; }
.dist-bar-wrap { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; }
.dist-bar { width: 18px; background: var(--el-color-primary-light-5); border-radius: 2px 2px 0 0; }
.dist-bar.correct { background: #67c23a; }
.dist-label { font-size: 11px; color: #909399; margin-top: 2px; }
</style>
