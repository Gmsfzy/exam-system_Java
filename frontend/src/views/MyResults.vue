<template>
  <div>
    <div class="page-header">
      <h2>我的成绩</h2>
    </div>
    <el-table :data="list" stripe>
      <el-table-column prop="examTitle" label="考试" min-width="220" />
      <el-table-column label="成绩" width="180">
        <template #default="{ row }">
          <strong :style="{ color: row.score / row.totalScore >= 0.6 ? '#67c23a' : '#f56c6c' }">
            {{ row.score }} / {{ row.totalScore }}
          </strong>
          <span style="margin-left: 8px; color: #909399;">
            ({{ ((row.score / row.totalScore) * 100).toFixed(1) }}%)
          </span>
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
</script>