<template>
  <div>
    <div class="page-header">
      <h2>学科分类</h2>
      <el-button type="primary" @click="openCreate">+ 新建学科</el-button>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="学科名称" />
      <el-table-column prop="description" label="描述" />
      <el-table-column prop="departmentName" label="所属院系" width="160" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button size="small" type="warning" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showModal" :title="editing ? '编辑学科' : '新建学科'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="学科名称" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="学科描述" /></el-form-item>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { majorAPI } from '../api/endpoints'

const list = ref([])
const showModal = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', description: '' })

onMounted(load)

async function load() {
  try { list.value = await majorAPI.list() || [] } catch (e) {}
}
function openCreate() { editing.value = false; Object.assign(form, { id: null, name: '', description: '' }); showModal.value = true }
function openEdit(row) { editing.value = true; Object.assign(form, { id: row.id, name: row.name, description: row.description || '' }); showModal.value = true }
async function submit() {
  if (!form.name) { ElMessage.warning('请输入名称'); return }
  try {
    if (editing.value) await majorAPI.update(form.id, form)
    else await majorAPI.create(form)
    ElMessage.success('保存成功')
    showModal.value = false
    load()
  } catch (e) { ElMessage.error('保存失败') }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除该学科？', '提示', { type: 'warning' })
    await majorAPI.remove(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (e) {}
}
</script>