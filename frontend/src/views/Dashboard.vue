<template>
  <div>
    <h2 style="margin-bottom: 20px;">概览</h2>
    <div class="stat-cards">
      <div class="stat-card">
        <div class="label">考试总数</div>
        <div class="value">{{ exams.length }}</div>
        <div class="icon">📝</div>
      </div>
      <div class="stat-card">
        <div class="label">题库题目</div>
        <div class="value">{{ questionsCount }}</div>
        <div class="icon">📚</div>
      </div>
      <div class="stat-card">
        <div class="label">学科分类</div>
        <div class="value">{{ majors.length }}</div>
        <div class="icon">📂</div>
      </div>
      <div class="stat-card">
        <div class="label">待阅卷</div>
        <div class="value">{{ pendingCount }}</div>
        <div class="icon">✍️</div>
      </div>
    </div>
    <div class="card">
      <h3 style="margin-bottom: 16px;">最近考试</h3>
      <el-table :data="exams.slice(0, 5)" stripe>
        <el-table-column prop="title" label="考试名称" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button size="small" @click="goManage(row.id)">管理</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { examAPI, questionAPI, majorAPI, resultAPI } from '../api/endpoints'

const router = useRouter()
const exams = ref([])
const questionsCount = ref(0)
const majors = ref([])
const pendingCount = ref(0)

onMounted(async () => {
  try {
    exams.value = await examAPI.list() || []
    majorAPI.list().then(d => { majors.value = d || [] }).catch(() => {})
    questionAPI.count().then(n => { questionsCount.value = n }).catch(() => {})
    // 待阅卷：汇总每场考试中含待人工评阅的学生数
    const lists = await Promise.all(
      exams.value.map(e => resultAPI.gradingList(e.id).catch(() => []))
    )
    pendingCount.value = lists
      .flat()
      .filter(s => s.hasPendingManual).length
  } catch (e) {}
})

function statusLabel(s) {
  return { draft: '草稿', published: '已发布', ended: '已结束' }[s] || s
}
function statusTagType(s) {
  return { draft: 'info', published: 'success', ended: 'danger' }[s] || ''
}
function goManage(id) { router.push('/exams/' + id + '/manage') }
</script>