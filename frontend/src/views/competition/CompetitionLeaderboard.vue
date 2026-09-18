<template>
  <div v-loading="loading">
    <div class="page-header"><h2>竞赛榜单</h2>
      <div>
        <el-button :icon="Refresh" circle @click="load" />
        <el-button style="margin-left:8px;" @click="router.back()">返回</el-button>
      </div>
    </div>
    <el-alert v-if="myRank" type="success" :closable="false" show-icon
              :title="`我的名次：第 ${myRank} 名`" style="margin-bottom:12px;" />
    <el-table :data="entries" border stripe>
      <el-table-column prop="rank" label="名次" width="80">
        <template #default="{ row }">
          <span v-if="row.rank <= 3">{{ ['🥇','🥈','🥉'][row.rank-1] }}</span>
          <span v-else>{{ row.rank }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="选手" />
      <el-table-column prop="score" label="得分" width="120" sortable />
      <el-table-column label="用时" width="120">
        <template #default="{ row }">{{ row.usedTime }} 秒</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !entries.length" description="暂无成绩，快来抢答第一名" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import { competitionAPI } from '../../api/endpoints'
import { subscribe, connectSocket } from '../../utils/socket'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const entries = ref([])
const myRank = ref(null)
const loading = ref(false)
let unsub = null, poll = null

async function load() {
  loading.value = true
  try {
    const lb = await competitionAPI.leaderboard(id)
    entries.value = lb.entries || []
    myRank.value = lb.myRank
  } catch (e) { /* noop */ } finally { loading.value = false }
}

onMounted(() => {
  connectSocket(); load()
  unsub = subscribe(`/topic/competition/${id}`, load)
  poll = setInterval(load, 30000)
})
onBeforeUnmount(() => { if (unsub) unsub(); if (poll) clearInterval(poll) })
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
</style>
