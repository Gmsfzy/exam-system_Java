<template>
  <div v-loading="loading">
    <div class="page-header"><h2>段位中心</h2><el-button :icon="Refresh" circle @click="load" /></div>

    <el-card v-if="me" class="tier-card">
      <div style="text-align:center;">
        <div style="font-size:56px;">{{ me.tierIcon }}</div>
        <div style="font-size:22px;font-weight:700;">{{ me.tier }}</div>
        <div style="color:#909399;margin-top:4px;">{{ me.season }} 赛季 · {{ me.points }} 分</div>
        <div style="margin-top:6px;color:#606266;">
          <span v-if="me.nextTier">距 {{ me.nextTier }}（{{ me.nextTierThreshold }}分）还差 {{ me.progressToNext }} 分</span>
          <span v-else>已达最高段位 👑</span>
        </div>
        <el-progress v-if="me.nextTier" :percentage="tierProgress" :show-text="false" style="max-width:320px;margin:12px auto 0;" />
        <div style="margin-top:12px;color:#909399;font-size:13px;">
          PK {{ me.pkWin }}胜/{{ me.pkDraw }}平/{{ me.pkLose }}负 · 胜率 {{ me.winRate }}% · 连胜 {{ me.streak }}（最高 {{ me.maxStreak }}）
        </div>
      </div>
    </el-card>

    <el-card style="margin-top:16px;">
      <template #header><strong>我的勋章墙</strong></template>
      <el-row :gutter="12">
        <el-col :span="6" v-for="b in badges" :key="b.badgeCode" style="margin-bottom:12px;">
          <div class="badge" :class="{ earned: b.earned }">
            <div style="font-size:32px;filter:grayscale(0);">{{ b.icon }}</div>
            <div style="font-weight:600;font-size:13px;">{{ b.name }}</div>
            <div style="font-size:11px;color:#909399;">{{ b.description }}</div>
            <div style="font-size:11px;margin-top:4px;">{{ b.earned ? ('✅ ' + b.season) : '未获得' }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-card style="margin-top:16px;">
      <template #header>
        <div style="display:flex;justify-content:space-between;">
          <strong>赛季排行榜</strong>
          <el-select v-model="season" size="small" style="width:140px;" @change="loadSeason">
            <el-option v-for="s in seasons" :key="s" :value="s" :label="s" />
          </el-select>
        </div>
      </template>
      <el-table :data="rankEntries" size="small" border>
        <el-table-column prop="rank" label="#" width="60" />
        <el-table-column prop="username" label="选手" />
        <el-table-column prop="points" label="积分" width="90" />
        <el-table-column prop="tier" label="段位" width="90" />
        <el-table-column prop="pkWin" label="PK胜" width="80" />
        <el-table-column prop="timedFinished" label="完赛" width="80" />
      </el-table>
    </el-card>

    <el-card style="margin-top:16px;" v-if="archives.length">
      <template #header><strong>历史赛季归档</strong></template>
      <el-table :data="archives" size="small" border>
        <el-table-column prop="season" label="赛季" />
        <el-table-column prop="rank" label="名次" width="80" />
        <el-table-column prop="points" label="积分" width="80" />
        <el-table-column prop="tier" label="段位" width="90" />
        <el-table-column prop="pkTotal" label="PK总场" width="90" />
        <el-table-column prop="timedFinished" label="完赛" width="80" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { gamificationAPI } from '../../api/endpoints'

const loading = ref(false)
const me = ref(null)
const badges = ref([])
const seasons = ref([])
const season = ref(null)
const rankEntries = ref([])
const archives = ref([])

const tierProgress = computed(() => {
  if (!me.value || !me.value.nextTier) return 100
  const prevMin = tierThreshold(me.value.tier)
  const nextMin = me.value.nextTierThreshold
  if (nextMin <= prevMin) return 100
  return Math.round(((me.value.points - prevMin) / (nextMin - prevMin)) * 100)
})
function tierThreshold(name) {
  return { '青铜': 0, '白银': 100, '黄金': 250, '铂金': 450, '钻石': 700, '王者': 1000 }[name] || 0
}

async function load() {
  loading.value = true
  try {
    me.value = await gamificationAPI.rankMe()
    badges.value = await gamificationAPI.badges()
    archives.value = await gamificationAPI.archives()
    seasons.value = await gamificationAPI.seasons()
    season.value = seasons.value[0]
    await loadSeason()
  } catch (e) { /* noop */ } finally { loading.value = false }
}

async function loadSeason() {
  if (!season.value) return
  try {
    const r = await gamificationAPI.seasonRank(season.value)
    rankEntries.value = r.entries || []
  } catch (e) { /* noop */ }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.tier-card { background: var(--brand-grad-soft); }
.badge { text-align: center; padding: 10px; border: 1px solid var(--line); border-radius: 8px; filter: grayscale(1); opacity: .55; }
.badge.earned { filter: none; opacity: 1; border-color: var(--el-color-warning); background: var(--wash-orange); }
</style>
