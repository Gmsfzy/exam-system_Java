<template>
  <div>
    <div class="page-header">
      <h2>自由刷题</h2>
      <div>
        <el-button type="primary" @click="openStart">开始新练习</el-button>
        <el-button @click="router.push('/learning')">返回学习中心</el-button>
      </div>
    </div>

    <el-card>
      <el-table :data="history" v-loading="loading" stripe>
        <el-table-column prop="title" label="会话标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'warning'">
              {{ row.status === 'completed' ? '已完成' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="正确 / 总数" width="120">
          <template #default="{ row }">{{ row.correctCount }} / {{ row.questionsCount }}</template>
        </el-table-column>
        <el-table-column label="正确率" width="100">
          <template #default="{ row }">
            {{ row.questionsCount ? ((row.correctCount / row.questionsCount) * 100).toFixed(0) + '%' : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="165" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" plain @click="router.push('/learning/practice/' + row.sessionId)">
              {{ row.status === 'completed' ? '查看' : '继续' }}
            </el-button>
            <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !history.length" description="还没有练习记录，点击右上角开始新练习" />
    </el-card>

    <el-dialog v-model="startVisible" title="开始新练习" width="480px">
      <el-form label-width="80px">
        <el-form-item label="专业">
          <el-select v-model="form.majorId" clearable filterable placeholder="不限" style="width: 100%;" @change="onMajorChange">
            <el-option v-for="m in majors" :key="m.id" :value="m.id" :label="m.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="课程">
          <el-select v-model="form.courseId" clearable filterable placeholder="不限" style="width: 100%;" :disabled="!form.majorId" @change="onCourseChange">
            <el-option v-for="c in courses" :key="c.id" :value="c.id" :label="c.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="form.chapterId" clearable filterable placeholder="不限" style="width: 100%;" :disabled="!form.courseId">
            <el-option v-for="ch in chapters" :key="ch.id" :value="ch.id" :label="ch.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="题数">
          <el-input-number v-model="form.count" :min="1" :max="50" />
          <span style="margin-left: 8px; color: #909399; font-size: 12px;">仅抽取客观题，单题即时判分</span>
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="留空默认「自由练习」" maxlength="64" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="doStart">开始</el-button>
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
const history = ref([])
const loading = ref(false)

const startVisible = ref(false)
const starting = ref(false)
const form = reactive({ majorId: null, courseId: null, chapterId: null, count: 10, title: '' })
const majors = ref([])
const courses = ref([])
const chapters = ref([])

async function load() {
  loading.value = true
  try {
    history.value = await learningAPI.practiceHistory() || []
  } catch (e) {} finally {
    loading.value = false
  }
}

async function openStart() {
  startVisible.value = true
  if (!majors.value.length) {
    try { majors.value = await dictAPI.majors() || [] } catch (e) {}
  }
}

async function onMajorChange(id) {
  form.courseId = null
  form.chapterId = null
  chapters.value = []
  courses.value = id ? await dictAPI.courses(id) || [] : []
}

async function onCourseChange(id) {
  form.chapterId = null
  chapters.value = id ? await dictAPI.chapters(id) || [] : []
}

async function doStart() {
  starting.value = true
  try {
    const res = await learningAPI.practiceStart({ ...form })
    startVisible.value = false
    router.push('/learning/practice/' + res.sessionId)
  } catch (e) { /* 拦截器已提示（如题库无题） */ } finally {
    starting.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('删除会话将同时清理其作答记录，确定？', '提示', { type: 'warning' })
    await learningAPI.practiceDelete(row.sessionId)
    ElMessage.success('已删除')
    load()
  } catch (e) {}
}

onMounted(load)
</script>
