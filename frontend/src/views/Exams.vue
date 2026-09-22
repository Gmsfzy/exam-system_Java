<template>
  <div>
    <div class="page-header">
      <h2>考试管理</h2>
      <el-button type="primary" @click="openCreate">+ 新建考试</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="title" label="考试名称" min-width="200" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="duration" label="时长(分钟)" width="100" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="成绩" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.resultsPublished === false ? 'warning' : 'success'">{{ row.resultsPublished === false ? '未发布' : '已发布' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340">
        <template #default="{ row }">
          <el-button size="small" @click="goManage(row.id)">管理</el-button>
          <el-button size="small" type="success" :disabled="row.status !== 'draft'" @click="publish(row)">发布</el-button>
          <el-button size="small" type="warning" :disabled="row.status === 'ended'" @click="end(row)">结束</el-button>
          <el-button size="small" type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showModal" :title="editing ? '编辑考试' : '新建考试'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="考试名称">
          <el-input v-model="form.title" placeholder="请输入考试名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入考试描述" />
        </el-form-item>
        <el-form-item label="时长">
          <el-input-number v-model="form.duration" :min="1" :max="600" style="width: 150px;" />
          <span style="margin-left: 10px; color: #909399;">分钟</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.startTime" type="datetime" placeholder="选择开始时间" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.endTime" type="datetime" placeholder="选择结束时间" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examAPI } from '../api/endpoints'

const router = useRouter()
const list = ref([])
const showModal = ref(false)
const editing = ref(false)
const form = reactive({ id: null, title: '', description: '', duration: 60, startTime: '', endTime: '' })

onMounted(load)

async function load() {
  try { list.value = await examAPI.list() || [] } catch (e) {}
}

function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, title: '', description: '', duration: 60, startTime: '', endTime: '' })
  showModal.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, { id: row.id, title: row.title, description: row.description || '', duration: row.duration, startTime: row.startTime || '', endTime: row.endTime || '' })
  showModal.value = true
}

async function submit() {
  if (!form.title) { ElMessage.warning('请输入考试名称'); return }
  try {
    const payload = {
      title: form.title,
      description: form.description,
      duration: form.duration,
      startTime: form.startTime || null,
      endTime: form.endTime || null
    }
    if (editing.value) await examAPI.update(form.id, payload)
    else await examAPI.create(payload)
    ElMessage.success('保存成功')
    showModal.value = false
    load()
  } catch (e) { ElMessage.error(e.response?.data?.message || '保存失败') }
}

async function publish(row) {
  try {
    await ElMessageBox.confirm(`确认发布「${row.title}」？发布后学生可见。`, '提示', { type: 'warning' })
    await examAPI.publish(row.id)
    ElMessage.success('已发布')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '发布失败')
  }
}

async function end(row) {
  try {
    await ElMessageBox.confirm(`确认结束「${row.title}」？`, '提示', { type: 'warning' })
    await examAPI.end(row.id)
    ElMessage.success('已结束')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除该考试？', '提示', { type: 'warning' })
    await examAPI.remove(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {}
}

function goManage(id) { router.push('/exams/' + id + '/manage') }
function statusLabel(s) { return { draft: '草稿', published: '已发布', ended: '已结束' }[s] || s }
function statusType(s) { return { draft: 'info', published: 'success', ended: 'danger' }[s] || '' }
</script>