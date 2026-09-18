<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>我的悬赏</h2>
      <el-button type="primary" :icon="Plus" @click="$router.push('/bounty/publish')">发布悬赏</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="我发布的" name="published">
        <el-empty v-if="!published.length" description="你还没有发布过悬赏" />
        <el-table v-else :data="published" border @row-click="go">
          <el-table-column prop="title" label="标题" show-overflow-tooltip />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ row.bountyType==='question'?'题目征集':'答案征集' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }"><el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="rewardPoints" label="赏金" width="80" />
          <el-table-column label="投稿/待审" width="110">
            <template #default="{ row }">{{ row.submissionCount }} / {{ row.pendingCount }}</template>
          </el-table-column>
          <el-table-column label="截止" width="150">
            <template #default="{ row }">{{ fmt(row.deadline) || '长期' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="我的投稿" name="submitted">
        <el-empty v-if="!submitted.length" description="你还没有投稿过" />
        <el-table v-else :data="submitted" border @row-click="e => go({ id: e.bountyId })">
          <el-table-column prop="bountyTitle" label="悬赏标题" show-overflow-tooltip />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ row.bountyType==='question'?'题目征集':'答案征集' }}</template>
          </el-table-column>
          <el-table-column prop="rewardPoints" label="赏金" width="80" />
          <el-table-column label="投稿状态" width="110">
            <template #default="{ row }"><el-tag :type="subStatusTag(row.submissionStatus)" size="small">{{ subStatusText(row.submissionStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="悬赏状态" width="110">
            <template #default="{ row }"><el-tag :type="statusTag(row.bountyStatus)" size="small">{{ statusText(row.bountyStatus) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="投稿时间" width="160">
            <template #default="{ row }">{{ fmt(row.submittedAt) }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus } from '@element-plus/icons-vue'
import { bountyAPI } from '../../api/endpoints'

const router = useRouter()
const loading = ref(false)
const tab = ref('published')
const published = ref([])
const submitted = ref([])

function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }
function statusText(s) { return { open: '征集中', closed: '已采纳', expired: '已过期' }[s] || s }
function statusTag(s) { return { open: 'warning', closed: 'success', expired: 'info' }[s] || '' }
function subStatusText(s) { return { pending: '待审核', accepted: '已采纳', rejected: '已拒绝' }[s] || s }
function subStatusTag(s) { return { pending: 'info', accepted: 'success', rejected: 'danger' }[s] || '' }
function go(row) { router.push('/bounty/' + row.id) }

async function load() {
  loading.value = true
  try {
    published.value = await bountyAPI.mine() || []
    submitted.value = await bountyAPI.mySubmissions() || []
  } catch (e) { /* noop */ } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
:deep(.el-table__row) { cursor: pointer; }
</style>
