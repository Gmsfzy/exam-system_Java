<template>
  <div>
    <div class="page-header">
      <h2>我的成绩</h2>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="examTitle" label="考试" min-width="220" />
      <el-table-column label="轮次" width="90">
        <template #default="{ row }">第 {{ row.attemptNo || 1 }} 次</template>
      </el-table-column>
      <el-table-column label="成绩" width="220">
        <template #default="{ row }">
          <span v-if="row.published === false" style="color: #e6a23c;">
            <el-tag size="small" type="warning">未发布</el-tag>
            <span style="margin-left: 6px;">成绩尚未公布</span>
          </span>
          <strong v-else :style="{ color: rate(row) >= 0.6 ? '#67c23a' : '#f56c6c' }">
            {{ row.score }} / {{ row.totalScore }}
            <span style="margin-left: 8px; color: #909399; font-weight: 400;">({{ (rate(row) * 100).toFixed(1) }}%)</span>
          </strong>
        </template>
      </el-table-column>
      <el-table-column label="申诉状态" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.reviewStatus && row.reviewStatus !== 'none'" size="small" :type="reviewType(row.reviewStatus)">
            {{ reviewLabel(row.reviewStatus) }}
          </el-tag>
          <span v-else style="color: #909399;">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="submittedAt" label="提交时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button size="small" @click="router.push('/my-results/' + row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { resultAPI } from '../api/endpoints'

const router = useRouter()
const list = ref([])

onMounted(async () => {
  try { list.value = await resultAPI.myResults() || [] } catch (e) {}
})

function rate(row) {
  if (row.score == null || row.totalScore == null || row.totalScore === 0) return 0
  return row.score / row.totalScore
}
function reviewLabel(s) { return { pending: '申诉中', approved: '已改分', rejected: '申诉驳回' }[s] || s }
function reviewType(s) { return { pending: 'warning', approved: 'success', rejected: 'info' }[s] || '' }
</script>
