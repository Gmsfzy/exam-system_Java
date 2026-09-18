<template>
  <div>
    <div class="page-header">
      <h2>题库管理</h2>
      <el-button type="success" @click="openGenerate" style="margin-right: 8px;">AI生成题目</el-button>
      <el-button type="primary" @click="openCreate">+ 新建题目</el-button>
    </div>

    <el-card style="margin-bottom: 16px;">
      <div style="display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
        <el-select v-model="filter.majorId" placeholder="学科" clearable style="width: 160px;" @change="load">
          <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
        </el-select>
        <el-select v-model="filter.type" placeholder="类型" clearable style="width: 140px;" @change="load">
          <el-option label="单选题" value="single_choice" />
          <el-option label="多选题" value="multiple_choice" />
          <el-option label="判断题" value="true_false" />
          <el-option label="填空题" value="fill_blank" />
          <el-option label="简答题" value="short_answer" />
          <el-option label="编程题" value="programming" />
          <el-option label="计算题" value="calculation" />
          <el-option label="应用题" value="application" />
            </el-select>
        <el-select v-model="filter.difficulty" placeholder="难度" clearable style="width: 140px;" @change="load">
          <el-option label="简单" value="easy" />
          <el-option label="中等" value="medium" />
          <el-option label="困难" value="hard" />
        </el-select>
        <span style="color: #909399; margin-left: auto;">共 {{ list.length }} 题</span>
      </div>
    </el-card>

    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column label="题目" min-width="320">
        <template #default="{ row }">
          <div style="white-space: pre-wrap;">{{ row.content }}</div>
          <div v-if="row.analysis" style="margin-top: 6px; padding: 6px 10px; background: var(--wash-cyan); border-radius: 4px; font-size: 12px; color: var(--ink-2); border-left: 3px solid var(--brand-3);">
            💡 {{ row.analysis }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="学科" width="120">
        <template #default="{ row }">{{ majorName(row.majorId) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }"><el-tag size="small">{{ typeLabel(row.type) }}</el-tag></template>
      </el-table-column>
      <el-table-column label="难度" width="100">
        <template #default="{ row }"><el-tag size="small" :type="diffType(row.difficulty)">{{ diffLabel(row.difficulty) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="answer" label="参考答案" width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showModal" :title="editing ? '编辑题目' : '新建题目'" width="640px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="题目内容">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="学科">
          <el-select v-model="form.majorId" style="width: 100%;">
            <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目类型">
          <el-select v-model="form.type" style="width: 100%;">
            <el-option label="单选题" value="single_choice" />
            <el-option label="多选题" value="multiple_choice" />
            <el-option label="判断题" value="true_false" />
            <el-option label="填空题" value="fill_blank" />
            <el-option label="简答题" value="short_answer" />
            <el-option label="编程题" value="programming" />
            <el-option label="计算题" value="calculation" />
            <el-option label="应用题" value="application" />
        </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" style="width: 100%;">
            <el-option label="简单" value="easy" />
            <el-option label="中等" value="medium" />
            <el-option label="困难" value="hard" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="hasOptions" label="选项">
          <div style="width: 100%;">
            <div v-for="(opt, idx) in form.options" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px;">
              <span style="padding: 8px 12px; background: var(--surface-2); border-radius: 4px; font-weight: 600;">{{ String.fromCharCode(65 + idx) }}.</span>
              <el-input v-model="form.options[idx]" style="flex: 1;" />
              <el-button size="small" type="danger" @click="form.options.splice(idx, 1)" v-if="form.options.length > 2">删除</el-button>
            </div>
            <el-button size="small" @click="form.options.push('')" style="margin-top: 4px;">+ 添加选项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="参考答案">
          <el-input v-model="form.answer" :type="hasOptions ? '' : 'textarea'" :rows="hasOptions ? 1 : 3" />
        </el-form-item>
        <el-form-item label="题目解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" placeholder="AI 可自动生成解析，帮助学生理解答案" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGenerate" title="AI 智能出题" width="720px" top="5vh">
      <el-tabs v-model="genActiveTab">
        <el-tab-pane label="1. 配置出题参数" name="config">
          <el-form label-width="90px">
            <el-form-item label="专业/学科">
              <el-select v-model="genForm.major" placeholder="选择已有或直接输入" filterable allow-create style="width: 100%;">
                <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.name" />
              </el-select>
            </el-form-item>
            <el-form-item label="题型">
              <el-select v-model="genForm.type" style="width: 100%;">
                <el-option label="单选题" value="single_choice" />
                <el-option label="多选题" value="multiple_choice" />
                <el-option label="判断题" value="true_false" />
                <el-option label="填空题" value="fill_blank" />
                <el-option label="简答题" value="short_answer" />
                <el-option label="编程题" value="programming" />
                <el-option label="计算题" value="calculation" />
                <el-option label="应用题" value="application" />
                <el-option label="作文" value="essay" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度">
              <el-radio-group v-model="genForm.difficulty">
                <el-radio-button label="easy">简单</el-radio-button>
                <el-radio-button label="medium">中等</el-radio-button>
                <el-radio-button label="hard">困难</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="专业提示词">
              <el-input
                v-model="genForm.keywords"
                type="textarea"
                :rows="4"
                placeholder="例如：面向对象、继承、多态、封装。输入与出题主题相关的关键词，AI 将围绕它们生成题目。" />
            </el-form-item>
            <el-form-item label="附加说明">
              <el-input
                v-model="genForm.hint"
                type="textarea"
                :rows="2"
                placeholder="可选：例如适合本科二年级、考察概念理解等附加要求" />
            </el-form-item>
            <el-form-item label="生成数量">
              <el-input-number v-model="genForm.count" :min="1" :max="10" />
            </el-form-item>
          </el-form>
          <div style="text-align: center; margin-top: 12px;">
            <el-button type="primary" :loading="genLoading" @click="doGenerate">🤖 AI 生成题目</el-button>
            <el-button @click="showGenerate = false">取消</el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="2. 编辑并保存题目" name="result">
          <el-alert v-if="!genQuestions.length" title="尚未生成题目，请先在配置出题参数中点击 AI生成题目" type="warning" :closable="false" style="margin-bottom: 12px;" />
          <div v-else style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;">
            <div>
              <el-button size="small" :disabled="genIdx === 0" @click="goGen(-1)">上一题</el-button>
              <span style="margin: 0 10px;">第 {{ genIdx + 1 }} / {{ genQuestions.length }} 题</span>
              <el-button size="small" :disabled="genIdx >= genQuestions.length - 1" @click="goGen(1)">下一题</el-button>
            </div>
            <el-tag v-if="genResult._saved" type="success">本题已保存</el-tag>
          </div>
          <el-form label-width="90px">
            <el-form-item label="题目类型">
              <el-select v-model="genResult.type" style="width: 100%;">
                <el-option label="单选题" value="single_choice" />
                <el-option label="多选题" value="multiple_choice" />
                <el-option label="判断题" value="true_false" />
                <el-option label="填空题" value="fill_blank" />
                <el-option label="简答题" value="short_answer" />
                <el-option label="编程题" value="programming" />
                <el-option label="计算题" value="calculation" />
                <el-option label="应用题" value="application" />
                <el-option label="作文" value="essay" />
              </el-select>
            </el-form-item>
            <el-form-item label="所属学科">
              <el-select v-model="genResult.majorId" placeholder="选择学科" style="width: 100%;">
                <el-option v-for="m in majors" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="难度">
              <el-radio-group v-model="genResult.difficulty">
                <el-radio-button label="easy">简单</el-radio-button>
                <el-radio-button label="medium">中等</el-radio-button>
                <el-radio-button label="hard">困难</el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="题目内容">
              <el-input v-model="genResult.content" type="textarea" :rows="4" />
            </el-form-item>
            <el-form-item v-if="genResult.type === 'single_choice' || genResult.type === 'multiple_choice'" label="选项">
              <div style="width: 100%;">
                <div v-for="(opt, idx) in genResult.options" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px;">
                  <span style="padding: 8px 12px; background: var(--surface-2); border-radius: 4px; font-weight: 600; min-width: 40px; text-align: center;">{{ String.fromCharCode(65 + idx) }}.</span>
                  <el-input v-model="genResult.options[idx]" style="flex: 1;" />
                  <el-button size="small" type="danger" @click="genResult.options.splice(idx, 1)" v-if="genResult.options.length > 2">删除</el-button>
                </div>
                <el-button size="small" @click="genResult.options.push('')" style="margin-top: 4px;">+ 添加选项</el-button>
              </div>
            </el-form-item>
            <el-form-item label="参考答案">
              <el-input v-model="genResult.answer" :type="genResultHasOptions ? '' : 'textarea'" :rows="genResultHasOptions ? 1 : 3" />
              <div v-if="genResultHasOptions" style="font-size: 12px; color: #909399; margin-top: 4px;">
                单选题填单个字母（如 B）；多选题填字母组合（如 ACD）；判断题填"正确"或"错误"。
              </div>
            </el-form-item>
            <el-form-item label="题目解析">
              <el-input v-model="genResult.analysis" type="textarea" :rows="3" placeholder="由 AI 生成的题目解析，可手动修改" />
            </el-form-item>
          </el-form>
          <div v-if="genQuestions.length" style="text-align: center; margin-top: 16px;">
            <el-button type="success" :loading="genLoading" @click="saveGenerated">💾 保存本题</el-button>
            <el-button type="success" plain :loading="genLoading" @click="saveAllGenerated">💾 全部保存（{{ genQuestions.length }} 题）</el-button>
            <el-button @click="genActiveTab = 'config'">返回配置</el-button>
            <el-button @click="showGenerate = false">取消</el-button>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { questionAPI, majorAPI } from '../api/endpoints'

const list = ref([])
const majors = ref([])
const filter = reactive({ majorId: null, type: '', difficulty: '' })
const showModal = ref(false)
const showGenerate = ref(false)
const genLoading = ref(false)
const editing = ref(false)
const form = reactive({ id: null, content: '', majorId: null, type: 'single_choice', difficulty: 'medium', options: ['', '', '', ''], answer: '', analysis: '' })
const genForm = reactive({ major: '', type: 'single_choice', difficulty: 'medium', keywords: '', hint: '', count: 1 })
const genActiveTab = ref('config')
const genQuestions = ref([])
const genIdx = ref(0)
const genResult = reactive({
  content: '', majorId: null,
  type: 'single_choice', difficulty: 'medium',
  options: ['', '', '', ''], answer: '', analysis: '', _saved: false
})
const genResultHasOptions = computed(() => ['single_choice', 'multiple_choice'].includes(genResult.type))
const hasOptions = computed(() => ['single_choice', 'multiple_choice'].includes(form.type))

onMounted(async () => {
  try { majors.value = await majorAPI.list() || [] } catch (e) {}
  load()
})

async function load() {
  try {
    const params = {}
    if (filter.majorId) params.majorId = filter.majorId
    if (filter.type) params.type = filter.type
    if (filter.difficulty) params.difficulty = filter.difficulty
    list.value = await questionAPI.list(params) || []
  } catch (e) {}
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, content: '', majorId: majors.value[0]?.id || null, type: 'single_choice', difficulty: 'medium', options: ['', '', '', ''], answer: '', analysis: '' })
  showModal.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, content: row.content, majorId: row.majorId, type: row.type, difficulty: row.difficulty, options: (row.options && row.options.length) ? [...row.options] : ['', '', '', ''], answer: row.answer || '', analysis: row.analysis || '' })
  showModal.value = true
}

async function submit() {
  if (!form.content) { ElMessage.warning('请输入题目内容'); return }
  try {
    const data = {
      content: form.content,
      majorId: form.majorId,
      type: form.type,
      difficulty: form.difficulty,
      answer: form.answer,
      analysis: form.analysis,
      options: hasOptions.value ? form.options.filter(o => o !== '') : null
    }
    if (editing.value) await questionAPI.update(form.id, data)
    else await questionAPI.create(data)
    ElMessage.success('保存成功')
    showModal.value = false
    load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除该题目？', '提示', { type: 'warning' })
    await questionAPI.remove(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {}
}

function openGenerate() {
  genQuestions.value = []
  genIdx.value = 0
  genActiveTab.value = 'config'
  showGenerate.value = true
}

async function doGenerate() {
  if (!genForm.major) { ElMessage.warning('请选择或输入专业/学科'); return }
  genLoading.value = true
  try {
    const hintParts = [genForm.keywords, genForm.hint].filter(Boolean)
    const payload = {
      major: genForm.major,
      type: genForm.type,
      difficulty: genForm.difficulty,
      hint: hintParts.join('；'),
      count: genForm.count || 1
    }
    const qs = await questionAPI.generate(payload)
    if (qs && qs.length) {
      genQuestions.value = qs.map(normalizeGen)
      genIdx.value = 0
      Object.assign(genResult, genQuestions.value[0])
      genActiveTab.value = 'result'
      ElMessage.success(`已生成 ${qs.length} 题，请检查后保存`)
    } else {
      ElMessage.warning('未生成题目，请调整参数后重试')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '生成失败（可能未配置 AI）')
  } finally {
    genLoading.value = false
  }
}

function normalizeGen(q) {
  return {
    content: q.content || '',
    majorId: majors.value.find(m => m.name === genForm.major)?.id || majors.value[0]?.id || null,
    type: q.type || genForm.type,
    difficulty: q.difficulty || genForm.difficulty,
    options: Array.isArray(q.options) && q.options.length ? [...q.options] : ['', '', '', ''],
    answer: q.answer || '',
    analysis: q.analysis || '',
    _saved: false
  }
}

// 切换生成题目前先把当前编辑写回数组
function syncGenBack() {
  const cur = genQuestions.value[genIdx.value]
  if (cur) Object.assign(cur, {
    content: genResult.content, majorId: genResult.majorId, type: genResult.type,
    difficulty: genResult.difficulty, options: [...genResult.options],
    answer: genResult.answer, analysis: genResult.analysis, _saved: genResult._saved
  })
}

function goGen(step) {
  syncGenBack()
  const next = genIdx.value + step
  if (next < 0 || next >= genQuestions.value.length) return
  genIdx.value = next
  Object.assign(genResult, genQuestions.value[next])
}

function buildGenPayload(q) {
  const choice = q.type === 'single_choice' || q.type === 'multiple_choice'
  return {
    content: q.content,
    majorId: q.majorId,
    type: q.type,
    difficulty: q.difficulty,
    options: choice ? q.options.filter(o => o !== '') : null,
    answer: q.answer,
    analysis: q.analysis
  }
}

async function saveGenerated() {
  if (!genResult.content) { ElMessage.warning('题目内容为空'); return }
  genLoading.value = true
  try {
    syncGenBack()
    const q = genQuestions.value[genIdx.value]
    await questionAPI.create(buildGenPayload(q))
    q._saved = true
    genResult._saved = true
    ElMessage.success('本题已保存到题库')
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    genLoading.value = false
  }
}

async function saveAllGenerated() {
  syncGenBack()
  const pending = genQuestions.value.filter(q => !q._saved && q.content)
  if (!pending.length) { ElMessage.info('没有待保存的题目'); return }
  genLoading.value = true
  let ok = 0
  try {
    for (const q of pending) {
      try {
        await questionAPI.create(buildGenPayload(q))
        q._saved = true
        ok++
      } catch (err) { /* 单题失败继续 */ }
    }
    ElMessage.success(`成功保存 ${ok}/${pending.length} 题`)
    if (ok === pending.length) {
      showGenerate.value = false
      genActiveTab.value = 'config'
    }
    load()
  } finally {
    genLoading.value = false
  }
}

function majorName(id) {
  return majors.value.find(m => m.id === id)?.name || '-'
}

function typeLabel(t) { return { single_choice: '单选', multiple_choice: '多选', true_false: '判断', fill_blank: '填空', short_answer: '简答', programming: '编程题', calculation: '计算题', application: '应用题' }[t] || t }
function diffLabel(d) { return { easy: '简单', medium: '中等', hard: '困难' }[d] || d }
function diffType(d) { return { easy: 'success', medium: 'warning', hard: 'danger' }[d] || '' }
</script>