<template>
  <div v-loading="loading">
    <div class="page-header"><h2>学情画像</h2><el-button :icon="Refresh" circle @click="load" /></div>

    <el-row :gutter="16" v-if="profile">
      <el-col :span="14">
        <el-card>
          <template #header><strong>五维能力雷达</strong></template>
          <div class="chart-wrap"><canvas ref="chartRef"></canvas></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card class="overview">
          <div class="ov-hero">
            <div class="ov-season">{{ profile.season }} 赛季 · {{ profile.tier }}</div>
            <div class="ov-score">{{ profile.overall }}</div>
            <div class="ov-sub">综合评分 · {{ profile.points }} 竞技积分</div>
          </div>
          <el-divider />
          <el-descriptions :column="2" size="small" border>
            <el-descriptions-item label="完赛场次">{{ profile.raw?.finishedCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="限时完赛">{{ profile.raw?.timedFinished ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="累计作答">{{ profile.raw?.totalAnswered ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="累计答对">{{ profile.raw?.totalCorrect ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="平均用时">{{ profile.raw?.avgTimeSpent ?? 0 }}s</el-descriptions-item>
            <el-descriptions-item label="当前连胜">{{ profile.raw?.streak ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="PK 胜/平/负">
              {{ profile.raw?.pkWin ?? 0 }}/{{ profile.raw?.pkDraw ?? 0 }}/{{ profile.raw?.pkLose ?? 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="最高连胜">{{ profile.raw?.maxStreak ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
        <el-card style="margin-top:16px;">
          <template #header><strong>维度明细</strong></template>
          <div v-for="d in profile.dimensions" :key="d.key" class="dim-row">
            <span class="dim-label">{{ d.label }}</span>
            <el-progress :percentage="Math.round(d.value)" :stroke-width="12" style="flex:1;" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-else-if="!loading" description="暂无画像数据，去参加一场竞赛吧" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { Chart } from 'chart.js/auto'
import { gamificationAPI } from '../../api/endpoints'
import { useThemeStore } from '../../stores/theme'

const theme = useThemeStore()
const loading = ref(false)
const profile = ref(null)
const chartRef = ref(null)
let chart = null

async function load() {
  loading.value = true
  try {
    profile.value = await gamificationAPI.myProfile()
    await nextTick()
    renderChart()
  } catch (e) { /* noop */ } finally { loading.value = false }
}

function renderChart() {
  if (!chartRef.value || !profile.value) return
  if (chart) { chart.destroy(); chart = null }
  const labels = profile.value.dimensions.map(d => d.label)
  const values = profile.value.dimensions.map(d => d.value)
  // 随主题取色：暗色下抬高文字/网格线亮度，避免坐标轴与维度名不可见
  const dark = theme.dark
  const text = dark ? '#c4ccdd' : '#525b73'
  const line = dark ? 'rgba(255,255,255,.16)' : 'rgba(0,0,0,.09)'
  const fill = dark ? 'rgba(124,138,255,.34)' : 'rgba(91,107,255,.20)'
  const border = dark ? 'rgba(140,152,255,.95)' : 'rgba(91,107,255,.95)'
  const point = dark ? '#8c98ff' : '#5b6bff'
  chart = new Chart(chartRef.value, {
    type: 'radar',
    data: {
      labels,
      datasets: [{
        label: '能力值',
        data: values,
        backgroundColor: fill,
        borderColor: border,
        pointBackgroundColor: point,
        pointBorderColor: point,
        borderWidth: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        r: {
          min: 0, max: 100,
          angleLines: { color: line },
          grid: { color: line },
          pointLabels: { color: text, font: { size: 13 } },
          ticks: { stepSize: 20, color: text, backdropColor: 'transparent', showLabelBackdrop: false }
        }
      },
      plugins: { legend: { display: false } }
    }
  })
}

watch(() => theme.dark, () => { if (profile.value) renderChart() })

onMounted(load)
onBeforeUnmount(() => { if (chart) chart.destroy() })
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.chart-wrap { position: relative; height: 360px; }
.overview { background: var(--brand-grad-soft); }
.ov-hero { text-align: center; }
.ov-season { font-size: 14px; color: var(--muted); }
.ov-score { font-size: 40px; font-weight: 800; margin: 6px 0; background: var(--brand-grad); -webkit-background-clip: text; background-clip: text; color: transparent; }
.ov-sub { font-size: 13px; color: var(--ink-2); }
.dim-row { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.dim-label { width: 56px; font-size: 13px; color: var(--ink-2); }
</style>
