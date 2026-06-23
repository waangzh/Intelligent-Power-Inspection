<template>
  <div>
    <PageHeader
      title="机器人管理"
      description="注册、绑定站点与运行状态监控"
      :breadcrumbs="[{ label: '资产感知' }, { label: '机器人管理' }]"
    >
      <template #actions>
        <el-button v-if="can('robot:manage')" type="primary" @click="dialogVisible = true">
          <el-icon><Plus /></el-icon>
          注册机器人
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6" v-for="s in statusStats" :key="s.label">
        <el-card shadow="never" class="mini-stat">
          <div class="val">{{ s.value }}</div>
          <div class="lbl">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="robotStore.robots" size="small">
        <el-table-column prop="name" label="名称" min-width="130" />
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column prop="serialNo" label="序列号" width="150" />
        <el-table-column label="绑定站点" width="150">
          <template #default="{ row }">{{ siteName(row.siteId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="电量" width="130">
          <template #default="{ row }">
            <el-progress :percentage="row.battery" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column prop="firmware" label="固件" width="90" />
        <el-table-column label="最后在线" width="160">
          <template #default="{ row }">{{ row.lastOnlineAt ? fmt(row.lastOnlineAt) : '-' }}</template>
        </el-table-column>
        <el-table-column v-if="can('robot:manage')" label="操作" width="80">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="remove(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="注册机器人" width="480px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="型号"><el-input v-model="form.model" /></el-form-item>
        <el-form-item label="序列号"><el-input v-model="form.serialNo" /></el-form-item>
        <el-form-item label="绑定站点">
          <el-select v-model="form.siteId" style="width: 100%">
            <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useRobotStore } from '@/stores/robot'
import { useSiteStore } from '@/stores/site'
import type { Robot } from '@/types'

const robotStore = useRobotStore()
const siteStore = useSiteStore()
const { can } = usePermission()
const dialogVisible = ref(false)
const form = reactive({ name: '', model: '', serialNo: '', siteId: siteStore.sites[0]?.id ?? '' })

const statusStats = computed(() => [
  { label: '总数', value: robotStore.robots.length },
  { label: '在线', value: robotStore.robots.filter((r) => r.status === 'ONLINE').length },
  { label: '任务中', value: robotStore.robots.filter((r) => r.status === 'BUSY').length },
  { label: '充电中', value: robotStore.robots.filter((r) => r.status === 'CHARGING').length },
])

function siteName(id?: string) {
  return id ? siteStore.getSiteById(id)?.name ?? '-' : '未绑定'
}

function statusType(s: Robot['status']) {
  return { ONLINE: 'success', BUSY: 'warning', CHARGING: 'info', OFFLINE: 'danger' }[s] as 'success' | 'warning' | 'info' | 'danger'
}

function fmt(iso: string) {
  return new Date(iso).toLocaleString('zh-CN')
}

function submit() {
  if (!form.name || !form.model) {
    ElMessage.warning('请填写完整信息')
    return
  }
  robotStore.addRobot({
    name: form.name,
    model: form.model,
    serialNo: form.serialNo || `SN-${Date.now()}`,
    siteId: form.siteId,
    status: 'OFFLINE',
    battery: 100,
    firmware: 'v1.0.0',
    lastOnlineAt: new Date().toISOString(),
  })
  dialogVisible.value = false
  ElMessage.success('机器人已注册')
}

function remove(id: string) {
  ElMessageBox.confirm('确定删除？', '确认', { type: 'warning' })
    .then(() => {
      robotStore.removeRobot(id)
      ElMessage.success('已删除')
    })
    .catch(() => {})
}
</script>

<style scoped>
.mini-stat .val {
  font-size: 24px;
  font-weight: 700;
  color: #1a5fb4;
}

.mini-stat .lbl {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
