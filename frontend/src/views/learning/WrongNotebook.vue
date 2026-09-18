<template>
  <div>
    <div class="page-header">
      <h2>错题本</h2>
      <el-button @click="router.push('/learning')">返回学习中心</el-button>
    </div>

    <el-card>
      <div style="display: flex; gap: 12px; margin-bottom: 12px; flex-wrap: wrap;">
        <el-select v-model="filters.sourceType" clearable placeholder="全部来源" style="width: 160px;" @change="load">
          <el-option value="exam" label="考试" />
          <el-option value="practice" label="练习" />
          <el-option value="competition" label="竞赛" />
        </el-select>
        <el-select v-model="filters.mastered" clearable placeholder="全部状态" style="width: 160px;" @change="load">
          <el-option :value="false" label="未掌握" />
          <el-option :value="true" label="已掌握" />
        </el-select>
        <el-statistic title="未掌握错题" :value="pendingCount" style="margin-left: auto;" />
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 24px;">
              <p><strong>我的错误答案：</strong><span style="color: #f56c6c;">{{ row.wrongAnswer || '（未作答）' }}</span></p>
              <p><strong>正确答案：</strong><span style="color: #67c23a;">{{ row.correctAnswer || '-' }}</span></p>
              <p v-if="row.analysis"><strong>解析：</strong>{{ row.analysis }}</p>
              <p v-if="row.knowledge"><strong>知识点：</strong>{{ row.knowledge }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="questionContent" label="题目" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.questionContent">{{ row.questionContent }}</span>
            <span v-else style="color: #c0c4cc;">（题目已从题库删除）</span>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="90">
          <template #default="{ row }">{{ typeLabel(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="来源" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="sourceTagType(row.sourceType)">{{ sourceLabel(row.sourceType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="wrongCount" label="错误次数" width="90" sortable />
        <el-table-column prop="lastWrongAt" label="最近错误" width="165" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isMastered ? 'success' : 'danger'">
              {{ row.isMastered ? '已掌握' : '未掌握' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.isMastered" size="small" type="success" plain @click="master(row)">标记掌握</el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !list.length" description="还没有错题，继续保持！" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { learningAPI } from '../../api/endpoints'

const router = useRouter()
const list = ref([])
const loading = ref(false)
const filters = reactive({ sourceType: null, mastered: null })

const pendingCount = computed(() => list.value.filter(r => !r.isMastered).length)

const TYPE_LABELS = {
  single_choice: '单选题', multiple_choice: '多选题', fill_blank: '填空题', true_false: '判断题',
  short_answer: '简答题', programming: '编程题', application: '应用题', calculation: '计算题'
}
function typeLabel(t) { return TYPE_LABELS[t] || t || '-' }
function sourceLabel(s) { return { exam: '考试', practice: '练习', competition: '竞赛' }[s] || s }
function sourceTagType(s) { return { exam: 'danger', practice: 'primary', competition: 'warning' }[s] || 'info' }

async function load() {
  loading.value = true
  try {
    list.value = await learningAPI.wrongRecords({
      sourceType: filters.sourceType || undefined,
      mastered: filters.mastered === null ? undefined : filters.mastered
    }) || []
  } catch (e) { /* 拦截器已提示 */ } finally {
    loading.value = false
  }
}

async function master(row) {
  try {
    await learningAPI.masterWrongRecord(row.id)
    ElMessage.success('已标记为掌握')
    load()
  } catch (e) {}
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除这条错题记录？', '提示', { type: 'warning' })
    await learningAPI.deleteWrongRecord(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {}
}

onMounted(load)
</script>
