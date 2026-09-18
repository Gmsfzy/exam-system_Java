<template>
  <div>
    <div class="page-header"><h2>发布悬赏</h2></div>
    <el-card style="max-width:720px;">
      <el-form :model="form" label-width="96px">
        <el-form-item label="悬赏类型">
          <el-radio-group v-model="form.bountyType">
            <el-radio-button value="question">题目征集</el-radio-button>
            <el-radio-button value="answer">答案征集</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="一句话描述你的征集" />
        </el-form-item>
        <el-form-item label="详细说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="对题目/答案的具体要求" />
        </el-form-item>
        <el-form-item label="赏金" required>
          <el-input-number v-model="form.rewardPoints" :min="1" :max="1000" />
          <span style="margin-left:8px;color:#909399;">竞技积分</span>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker v-model="form.deadline" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空表示长期有效" />
        </el-form-item>

        <template v-if="form.bountyType === 'question'">
          <el-form-item label="期望专业" required>
            <el-select v-model="form.majorId" filterable placeholder="选择题库专业">
              <el-option v-for="m in majors" :key="m.id" :value="m.id" :label="m.name" />
            </el-select>
          </el-form-item>
          <el-form-item label="期望题型">
            <el-select v-model="form.qType" clearable placeholder="缺省单选题">
              <el-option v-for="t in qTypes" :key="t.value" :value="t.value" :label="t.label" />
            </el-select>
          </el-form-item>
          <el-form-item label="期望难度">
            <el-select v-model="form.qDifficulty" clearable placeholder="缺省中等">
              <el-option value="easy" label="简单" /><el-option value="medium" label="中等" /><el-option value="hard" label="困难" />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="题源">
            <el-radio-group v-model="answerSource">
              <el-radio-button value="bank">关联题库题</el-radio-button>
              <el-radio-button value="snapshot">自带题目</el-radio-button>
            </el-radio-group>
          </el-form-item>
         <el-form-item v-if="answerSource === 'bank'" label="题库题目">
            <el-select v-model="form.targetQuestionId" filterable placeholder="选择题库已有题">
              <el-option v-for="q in questionOpts" :key="q.id" :value="q.id" :label="q.label" />
            </el-select>
          </el-form-item>
          <template v-else>
            <el-form-item label="题目内容"><el-input v-model="snapshot.content" type="textarea" :rows="2" /></el-form-item>
            <el-form-item label="选项"><el-input v-model="snapshot.optionsText" type="textarea" :rows="3" placeholder="每行一个选项，可留空" /></el-form-item>
            <el-form-item label="参考答案"><el-input v-model="snapshot.answer" placeholder="仅你本人可见" /></el-form-item>
          </template>
        </template>

        <el-form-item>
          <el-button type="primary" @click="submit">发布</el-button>
          <el-button @click="$router.push('/bounty')">返回广场</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { bountyAPI, majorAPI, questionAPI } from '../../api/endpoints'

const router = useRouter()
const majors = ref([])
const questionOpts = ref([])
const answerSource = ref('bank')
const qTypes = [
  { value: 'single_choice', label: '单选题' }, { value: 'multiple_choice', label: '多选题' },
  { value: 'true_false', label: '判断题' }, { value: 'fill_blank', label: '填空题' },
  { value: 'short_answer', label: '简答题' }
]
const form = reactive({
  bountyType: 'question', title: '', description: '', rewardPoints: 10,
  deadline: null, majorId: null, qType: '', qDifficulty: '', targetQuestionId: null
})
const snapshot = reactive({ content: '', optionsText: '', answer: '' })

async function loadQuestions() {
  try {
    const items = await questionAPI.list({ size: 200 }) || []
    questionOpts.value = items.map(q => ({ id: q.id, label: (q.content || '').slice(0, 40) }))
  } catch (e) { /* noop */ }
}

async function submit() {
  if (!form.title) { ElMessage.warning('请填写标题'); return }
  const payload = { ...form }
  if (form.bountyType === 'answer') {
    if (answerSource.value === 'bank') {
      payload.targetQuestionSnapshot = null
      if (!form.targetQuestionId) { ElMessage.warning('请选择关联的题库题目'); return }
    } else {
      payload.targetQuestionId = null
      const options = snapshot.optionsText.split('\n').map(s => s.trim()).filter(Boolean)
      payload.targetQuestionSnapshot = { content: snapshot.content, options, answer: snapshot.answer }
      if (!snapshot.content) { ElMessage.warning('请填写题目内容'); return }
    }
  } else {
    if (!form.majorId) { ElMessage.warning('题目征集必须选择专业'); return }
    payload.targetQuestionId = null
    payload.targetQuestionSnapshot = null
  }
  try {
    const b = await bountyAPI.publish(payload)
    ElMessage.success('发布成功')
    router.push('/bounty/' + b.id)
  } catch (e) { /* noop */ }
}

onMounted(async () => {
  try { majors.value = await majorAPI.list() || [] } catch (e) { /* noop */ }
  loadQuestions()
})
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
