<template>
  <div>
    <div class="page-header">
      <h2>学习报告</h2>
      <el-button @click="router.push('/learning')">返回学习中心</el-button>
    </div>

    <el-row :gutter="16" v-loading="loadingOverview">
      <el-col :span="6" v-for="s in statCards" :key="s.label">
        <el-card shadow="never">
          <el-statistic :title="s.label" :value="s.value" :formatter="s.formatter" />
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top: 16px;">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <strong>每日学习趋势</strong>
          <el-radio-group v-model="days" size="small" @change="loadDaily">
            <el-radio-button :value="7">近 7 天</el-radio-button>
            <el-radio-button :value="14">近 14 天</el-radio-button>
            <el-radio-button :value="30">近 30 天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div class="bar-chart" v-loading="loadingDaily">
        <div class="bar-col" v-for="d in daily" :key="d.date">
          <div class="bar-wrap" :title="`${d.date}：${d.totalQuestions} 题，正确率 ${pct(d.accuracy)}，${d.timeSpentSec}s`">
            <div class="bar" :style="{ height: barHeight(d.totalQuestions) + 'px' }"
                 :class="{ empty: !d.totalQuestions }"></div>
          </div>
          <div class="bar-label">{{ d.date.slice(5) }}</div>
        </div>
        <el-empty v-if="!daily.length" description="暂无数据" />
      </div>
      <el-table :data="daily.filter(x => x.totalQuestions > 0)" stripe size="small" style="margin-top: 8px;">
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="totalQuestions" label="题数" width="80" />
        <el-table-column prop="correctCount" label="答对" width="80" />
        <el-table-column label="正确率" width="100">
          <template #default="{ row }">{{ pct(row.accuracy) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="110">
          <template #default="{ row }">{{ formatDuration(row.timeSpentSec) }}</template>
        </el-table-column>
        <el-table-column prop="examCount" label="考试" width="80" />
        <el-table-column prop="practiceCount" label="练习" width="80" />
        <el-table-column prop="competitionCount" label="竞赛" width="80" />
      </el-table>
      <el-empty v-if="!loadingDaily && !daily.some(x => x.totalQuestions > 0)" description="所选区间暂无学习记录" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { learningAPI } from '../../api/endpoints'

const router = useRouter()
const overview = ref(null)
const daily = ref([])
const days = ref(7)
const loadingOverview = ref(false)
const loadingDaily = ref(false)

const statCards = computed(() => {
  const o = overview.value || {}
  return [
    { label: '累计学习题数', value: o.totalQuestions || 0, formatter: v => v },
    { label: '综合正确率', value: (o.accuracy || 0) * 100, formatter: v => v.toFixed(1) + '%' },
    { label: '待攻克错题', value: o.wrongPendingCount || 0, formatter: v => v },
    { label: '进行中计划', value: o.activePlanCount || 0, formatter: v => v }
  ]
})

const maxTotal = computed(() => Math.max(1, ...daily.value.map(d => d.totalQuestions)))

function barHeight(v) {
  return v ? Math.max(6, Math.round(v / maxTotal.value * 120)) : 0
}
function pct(a) { return ((a || 0) * 100).toFixed(0) + '%' }
function formatDuration(sec) {
  if (!sec) return '0 分钟'
  const h = Math.floor(sec / 3600)
  const m = Math.round((sec % 3600) / 60)
  return h > 0 ? `${h}h ${m}m` : `${m} 分钟`
}

async function loadOverview() {
  loadingOverview.value = true
  try { overview.value = await learningAPI.reportOverview() } catch (e) {} finally { loadingOverview.value = false }
}

async function loadDaily() {
  loadingDaily.value = true
  try { daily.value = await learningAPI.reportDaily(days.value) || [] } catch (e) {} finally { loadingDaily.value = false }
}

onMounted(() => { loadOverview(); loadDaily() })
</script>

<style scoped>
.bar-chart { display: flex; align-items: flex-end; gap: 6px; min-height: 150px; padding: 8px 4px 0; overflow-x: auto; }
.bar-col { display: flex; flex-direction: column; align-items: center; min-width: 30px; }
.bar-wrap { display: flex; align-items: flex-end; height: 124px; }
.bar { width: 18px; background: linear-gradient(180deg, #79bbff, #409eff); border-radius: 3px 3px 0 0; }
.bar.empty { background: #ebeef5; height: 2px !important; }
.bar-label { font-size: 11px; color: #909399; margin-top: 4px; white-space: nowrap; }
</style>
