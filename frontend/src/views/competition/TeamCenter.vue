<template>
  <div v-loading="loading">
    <div class="page-header"><h2>战队中心</h2>
      <el-button type="primary" :icon="Plus" @click="createVisible = true">创建战队</el-button>
    </div>

    <el-card v-if="myTeam" style="margin-bottom:16px;" class="my-team">
      <template #header><strong>我的战队：{{ myTeam.name }}</strong></template>
      <div style="color:#606266;margin-bottom:8px;">{{ myTeam.description || '—' }} · 队长：{{ myTeam.captainName }} · 成员 {{ myTeam.memberCount }} 人</div>
      <el-table :data="myTeam.members" size="small" border>
        <el-table-column prop="username" label="成员" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="{ row }"><el-tag :type="row.role==='captain'?'warning':''" size="small">{{ row.role==='captain'?'队长':'成员' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="140" v-if="isCaptain">
          <template #default="{ row }">
            <el-button v-if="row.userId !== myTeam.captainId" size="small" link type="primary" @click="transfer(row)">转让队长</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:12px;">
        <el-button size="small" @click="leave">退出战队</el-button>
        <el-button v-if="isCaptain" size="small" type="danger" @click="disband">解散战队</el-button>
      </div>
    </el-card>

    <el-card v-else style="margin-bottom:16px;">
      <el-empty description="你还没有加入战队">
        <el-button type="primary" @click="createVisible = true">创建一个</el-button>
      </el-empty>
    </el-card>

    <el-card>
      <template #header><strong>全部战队</strong></template>
      <el-table :data="teams" border>
        <el-table-column prop="name" label="战队" />
        <el-table-column prop="description" label="简介" show-overflow-tooltip />
        <el-table-column prop="captainName" label="队长" width="140" />
        <el-table-column prop="memberCount" label="人数" width="80" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!!myTeam || row.mine" @click="join(row)">加入</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" title="创建战队" width="420px">
      <el-form :model="form" label-width="70px;">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="简介"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible=false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { teamAPI } from '../../api/endpoints'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const loading = ref(false)
const teams = ref([])
const myTeam = ref(null)
const createVisible = ref(false)
const form = reactive({ name: '', description: '' })

const isCaptain = computed(() => myTeam.value && myTeam.value.captainId === auth.currentUser?.id)

async function load() {
  loading.value = true
  try {
    teams.value = await teamAPI.list() || []
    myTeam.value = await teamAPI.mine()
  } catch (e) { /* noop */ } finally { loading.value = false }
}

async function create() {
  if (!form.name) { ElMessage.warning('请输入战队名称'); return }
  try { await teamAPI.create({ name: form.name, description: form.description }); ElMessage.success('创建成功，已获「开疆辟土」勋章'); createVisible.value = false; form.name=''; form.description=''; load() }
  catch (e) { /* noop */ }
}
async function join(row) { try { await teamAPI.join(row.id); ElMessage.success('加入成功'); load() } catch (e) { /* noop */ } }
async function leave() {
  try { await ElMessageBox.confirm('确认退出战队？', '提示', { type: 'warning' }); await teamAPI.leave(myTeam.value.id); load() } catch (e) { /* noop */ }
}
async function disband() {
  try { await ElMessageBox.confirm('确认解散战队？', '提示', { type: 'warning' }); await teamAPI.disband(myTeam.value.id); load() } catch (e) { /* noop */ }
}
async function transfer(row) {
  try { await teamAPI.transfer(myTeam.value.id, row.userId); ElMessage.success('已转让队长'); load() } catch (e) { /* noop */ }
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; justify-content: space-between; align-items: center; }
.my-team { border-color: #e6a23c; }
</style>
