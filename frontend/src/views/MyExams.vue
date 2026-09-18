<template>
  <div>
    <div class="page-header">
      <h2>我的考试</h2>
      <div style="display: flex; gap: 8px;">
        <el-input v-model="joinCode" placeholder="输入邀请码" style="width: 180px;" @keyup.enter="join" />
        <el-button type="success" @click="join">邀请码加入</el-button>
      </div>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="title" label="考试名称" min-width="200" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="duration" label="时长(分钟)" width="110" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" type="primary" :disabled="row.status !== 'published'" @click="take(row.id)">参加考试</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { examAPI } from '../api/endpoints'

const router = useRouter()
const list = ref([])
const joinCode = ref('')

onMounted(load)

async function load() {
  try { list.value = await examAPI.list() || [] } catch (e) {}
}

async function join() {
  const code = (joinCode.value || '').trim()
  if (!code) { ElMessage.warning('请输入邀请码'); return }
  try {
    await examAPI.joinByCode(code)
    ElMessage.success('已加入考试')
    joinCode.value = ''
    load()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加入失败，请检查邀请码')
  }
}

function take(id) { router.push('/take/' + id) }
function statusLabel(s) { return { draft: '草稿', published: '已发布', ended: '已结束' }[s] || s }
function statusType(s) { return { draft: 'info', published: 'success', ended: 'danger' }[s] || '' }
</script>