<template>
  <div>
    <div class="page-header">
      <h2>学习计划</h2>
      <div>
        <el-button type="primary" @click="openCreate">新建计划</el-button>
        <el-button @click="router.push('/learning')">返回学习中心</el-button>
      </div>
    </div>

    <el-card>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="title" label="计划名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="160" show-overflow-tooltip />
        <el-table-column label="进度" width="220">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.min(100, Math.round(row.completedCount / row.targetCount * 100))"
              :status="row.status === 'completed' ? 'success' : undefined"
              :format="() => `${row.completedCount}/${row.targetCount}`" />
          </template>
        </el-table-column>
        <el-table-column label="期限" width="200">
          <template #default="{ row }">
            {{ row.startDate || '?' }} ~ {{ row.endDate || '?' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" plain @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 'completed'" size="small" type="warning" plain @click="togglePause(row)">
              {{ row.status === 'active' ? '暂停' : '恢复' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="暂无学习计划" />
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑计划' : '新建计划'" width="520px">
      <el-form label-width="90px">
        <el-form-item label="计划名称" required>
          <el-input v-model="form.title" maxlength="128" placeholder="如：数据结构第二章专项" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="目标题数">
          <el-input-number v-model="form.targetCount" :min="1" :max="10000" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">完成刷题后自动累加进度</span>
        </el-form-item>
        <el-form-item label="专业">
          <el-select v-model="form.majorId" clearable filterable placeholder="不限" style="width: 100%;" @change="onMajorChange">
            <el-option v-for="m in majors" :key="m.id" :value="m.id" :label="m.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" clearable filterable placeholder="不限" style="width: 100%;" :disabled="!form.majorId">
            <el-option v-for="c in courses" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="期限">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                          start-placeholder="开始日期" end-placeholder="结束日期" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { learningAPI, dictAPI } from '../../api/endpoints'

const router = useRouter()
const list = ref([])
const loading = ref(false)

const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({ title: '', description: '', targetCount: 10, majorId: null, courseId: null })
const dateRange = ref(null)
const majors = ref([])
const courses = ref([])

function statusLabel(s) { return { active: '进行中', paused: '已暂停', completed: '已完成' }[s] || s }
function statusTag(s) { return { active: 'primary', paused: 'warning', completed: 'success' }[s] || 'info' }

async function load() {
  loading.value = true
  try { list.value = await learningAPI.plans() || [] } catch (e) {} finally { loading.value = false }
}

async function ensureDicts() {
  if (!majors.value.length) {
    try { majors.value = await dictAPI.majors() || [] } catch (e) {}
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { title: '', description: '', targetCount: 10, majorId: null, courseId: null })
  dateRange.value = null
  courses.value = []
  dialogVisible.value = true
  ensureDicts()
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title, description: row.description, targetCount: row.targetCount,
    majorId: row.majorId, courseId: row.courseId
  })
  dateRange.value = row.startDate && row.endDate ? [row.startDate, row.endDate] : null
  dialogVisible.value = true
  ensureDicts().then(async () => {
    if (form.majorId) courses.value = await dictAPI.courses(form.majorId) || []
  })
}

async function onMajorChange(id) {
  form.courseId = null
  courses.value = id ? await dictAPI.courses(id) || [] : []
}

async function save() {
  if (!form.title.trim()) { ElMessage.warning('请填写计划名称'); return }
  saving.value = true
  const payload = {
    ...form,
    startDate: dateRange.value?.[0] || null,
    endDate: dateRange.value?.[1] || null
  }
  try {
    if (editingId.value) await learningAPI.updatePlan(editingId.value, payload)
    else await learningAPI.createPlan(payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e) {} finally { saving.value = false }
}

async function togglePause(row) {
  try {
    await learningAPI.togglePausePlan(row.id)
    ElMessage.success(row.status === 'active' ? '已暂停' : '已恢复')
    load()
  } catch (e) {}
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除计划「${row.title}」？`, '提示', { type: 'warning' })
    await learningAPI.deletePlan(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {}
}

onMounted(load)
</script>
