<template>
  <div class="profile-section">
    <h3 class="section-title"><span class="title-bar" />偏好设置</h3>
    <p class="section-desc">自定义通知与界面偏好</p>

    <el-form label-width="120px" class="settings-form">
      <div class="settings-group">
        <h4 class="group-title">消息通知</h4>
        <el-form-item label="告警通知">
          <el-switch v-model="form.notifyAlarm" />
          <span class="item-hint">有新告警时提醒</span>
        </el-form-item>
        <el-form-item label="任务通知">
          <el-switch v-model="form.notifyTask" />
          <span class="item-hint">任务状态变更时提醒</span>
        </el-form-item>
        <el-form-item label="系统通知">
          <el-switch v-model="form.notifySystem" />
          <span class="item-hint">系统公告与维护提醒</span>
        </el-form-item>
      </div>

      <div class="settings-group">
        <h4 class="group-title">界面偏好</h4>
        <el-form-item label="默认站点">
          <el-select v-model="form.defaultSiteId" clearable placeholder="进入总览时默认站点" style="width: 280px">
            <el-option v-for="s in siteStore.sites" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="侧边栏折叠">
          <el-switch v-model="form.sidebarCollapsed" />
          <span class="item-hint">默认收起左侧菜单</span>
        </el-form-item>
      </div>

      <div v-if="can('alarm:policy')" class="settings-group">
        <h4 class="group-title">告警转工单策略</h4>
        <p class="group-desc">按告警级别配置自动或人工转工单，保存后写入后端并立即生效</p>
        <AlarmEscalationPolicy v-model:rules="policyRules" />
      </div>
    </el-form>

    <div class="form-footer">
      <el-button type="primary" :loading="saving" @click="handleSave">保存设置</el-button>
      <el-button @click="loadPrefs">重置</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AlarmEscalationPolicy from '@/components/AlarmEscalationPolicy.vue'
import { usePermission } from '@/composables/usePermission'
import { useAlarmStore } from '@/stores/alarm'
import { useAuthStore } from '@/stores/auth'
import { useProfileStore } from '@/stores/profile'
import { useSiteStore } from '@/stores/site'
import type { UserPreferences } from '@/types/auth'
import type { AlarmSeverity, AlarmWorkOrderMode } from '@/types'

const authStore = useAuthStore()
const profileStore = useProfileStore()
const siteStore = useSiteStore()
const alarmStore = useAlarmStore()
const { can } = usePermission()
const saving = ref(false)

const form = reactive<UserPreferences>({
  notifyAlarm: true,
  notifyTask: true,
  notifySystem: true,
  defaultSiteId: undefined,
  sidebarCollapsed: false,
})

const policyRules = reactive<Record<AlarmSeverity, AlarmWorkOrderMode>>({
  CRITICAL: 'AUTO',
  HIGH: 'AUTO',
  MEDIUM: 'MANUAL',
  LOW: 'MANUAL',
})

async function loadPrefs() {
  if (!authStore.user) return
  const prefs = profileStore.loadPreferences(authStore.user.id)
  Object.assign(form, prefs)
  if (can('alarm:policy')) {
    await alarmStore.loadWorkOrderPolicy()
    Object.assign(policyRules, alarmStore.workOrderPolicy.rules)
  }
}

async function handleSave() {
  if (!authStore.user) return

  saving.value = true
  try {
    await profileStore.savePreferences(authStore.user.id, { ...form })
    if (can('alarm:policy')) {
      await alarmStore.saveWorkOrderPolicy({ ...policyRules })
    }
    ElMessage.success('偏好设置已保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadPrefs)
</script>

<style scoped>
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
  color: #1a2b3c;
}

.title-bar {
  display: inline-block;
  width: 4px;
  height: 18px;
  background: #1a5fb4;
  border-radius: 2px;
}

.section-desc {
  margin: 0 0 28px;
  font-size: 13px;
  color: #909399;
}

.settings-group {
  margin-bottom: 28px;
}

.group-title {
  margin: 0 0 16px;
  padding-bottom: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1a2b3c;
  border-bottom: 1px solid #f0f2f5;
}

.group-desc {
  margin: -8px 0 14px;
  font-size: 12px;
  color: #909399;
}

.item-hint {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.form-footer {
  margin-top: 16px;
  padding-top: 24px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  justify-content: center;
  gap: 12px;
}
</style>
