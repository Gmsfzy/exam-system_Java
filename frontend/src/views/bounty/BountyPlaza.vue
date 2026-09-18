<template>
  <div v-loading="loading">
    <div class="page-header">
      <h2>悬赏广场</h2>
      <div>
        <el-radio-group v-model="type" size="small" @change="load" style="margin-right:12px;">
          <el-radio-button value="">全部</el-radio-button>
          <el-radio-button value="question">题目征集</el-radio-button>
          <el-radio-button value="answer">答案征集</el-radio-button>
        </el-radio-group>
        <el-select v-model="status" size="small" style="width:120px;margin-right:12px;" @change="load">
          <el-option value="" label="全部状态" />
          <el-option value="open" label="征集中" />
          <el-option value="closed" label="已采纳" />
          <el-option value="expired" label="已过期" />
        </el-select>
        <el-button type="primary" :icon="Plus" @click="$router.push('/bounty/publish')">发布悬赏</el-button>
      </div>
    </div>

    <el-empty v-if="!list.length && !loading" description="暂无悬赏" />
    <el-row :gutter="16">
      <el-col :span="8" v-for="b in list" :key="b.id" style="margin-bottom:16px;">
        <el-card class="bounty-card" shadow="hover" @click="$router.push('/bounty/' + b.id)">
          <div class="card-top">
            <el-tag :type="b.bountyType==='question'?'primary':'success'" size="small">{{ b.bountyType==='question'?'题目征集':'答案征集' }}</el-tag>
            <el-tag :type="statusTag(b.status)" size="small">{{ statusText(b.status) }}</el-tag>
            <span class="reward">💰 {{ b.rewardPoints }}</span>
          </div>
          <div class="title">{{ b.title }}</div>
          <div class="desc">{{ b.description || '—' }}</div>
          <div class="meta">
            <span>{{ b.publisherName }}</span>
            <span v-if="b.majorName"> · {{ b.majorName }}</span>
          </div>
          <div class="foot">
            <span>投稿 {{ b.submissionCount }}（待审 {{ b.pendingCount }}）</span>
            <span v-if="b.deadline">截止 {{ fmt(b.deadline) }}</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { bountyAPI } from '../../api/endpoints'

const loading = ref(false)
const list = ref([])
const type = ref('')
const status = ref('')

function statusText(s) { return { open: '征集中', closed: '已采纳', expired: '已过期' }[s] || s }
function statusTag(s) { return { open: 'warning', closed: 'success', expired: 'info' }[s] || '' }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '' }

async function load() {
  loading.value = true
  try { list.value = await bountyAPI.plaza({ type: type.value, status: status.value }) || [] }
  catch (e) { /* noop */ } finally { loading.value = false }
}
onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.bounty-card { cursor: pointer; }
.card-top { display: flex; align-items: center; gap: 8px; }
.reward { margin-left: auto; color: #e6a23c; font-weight: 700; }
.title { font-size: 16px; font-weight: 600; margin: 8px 0 4px; }
.desc { color: #606266; font-size: 13px; min-height: 34px; overflow: hidden; }
.meta { color: #909399; font-size: 12px; margin: 6px 0; }
.foot { display: flex; justify-content: space-between; color: #909399; font-size: 12px; }
</style>
