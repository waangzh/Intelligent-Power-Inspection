<template>
  <div>
    <PageHeader
      title="检测策略"
      description="路线级与检查点级检测模板，集成 LocateAnything 自然语言提示"
      :breadcrumbs="[{ label: '资产感知' }, { label: '检测策略' }]"
    >
      <template #actions>
        <el-button v-if="can('detection:manage')" type="primary" @click="dialogVisible = true">
          <el-icon><Plus /></el-icon>
          新建模板
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>LocateAnything 说明</template>
          <p class="info-text">
            NVIDIA LocateAnything-3B 支持自然语言提示检测复杂目标。在检查点检测中配置提示词，例如「红色刀闸开关」「变压器底部渗油区域」，即可实现开放词汇定位。
          </p>
          <el-link href="https://huggingface.co/nvidia/LocateAnything-3B" target="_blank" type="primary">
            查看模型文档
          </el-link>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>检测层级</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="路线级">人员、安全帽、障碍物、火源（行进中持续）</el-descriptions-item>
            <el-descriptions-item label="检查点级">开关、表计、漏油、烟火、异物（到点触发）</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never">
      <el-table :data="detectionStore.templates" size="small">
        <el-table-column prop="name" label="模板名称" min-width="140" />
        <el-table-column label="适用范围" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.scope === 'ROUTE' ? '路线级' : '检查点级' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="检测项" min-width="200">
          <template #default="{ row }: { row: import('@/types').DetectionTemplate }">
            <el-tag v-for="t in row.types" :key="t" size="small" style="margin: 2px">{{ DETECTION_LABELS[t as keyof typeof DETECTION_LABELS] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" show-overflow-tooltip />
        <el-table-column label="提示词" min-width="160">
          <template #default="{ row }">
            <span v-for="(v, k) in row.prompts" :key="k" class="prompt">{{ k }}: {{ v }} </span>
            <span v-if="!Object.keys(row.prompts).length">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="can('detection:manage')" label="操作" width="80">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="detectionStore.removeTemplate(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新建检测模板" width="500px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="范围">
          <el-radio-group v-model="form.scope">
            <el-radio value="ROUTE">路线级</el-radio>
            <el-radio value="CHECKPOINT">检查点级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useDetectionStore } from '@/stores/detection'
import { CHECKPOINT_DETECTIONS, DETECTION_LABELS, ROUTE_DETECTIONS } from '@/types'

const detectionStore = useDetectionStore()
const { can } = usePermission()
const dialogVisible = ref(false)
const form = reactive({ name: '', scope: 'ROUTE' as 'ROUTE' | 'CHECKPOINT', description: '' })

function create() {
  if (!form.name) {
    ElMessage.warning('请填写名称')
    return
  }
  detectionStore.addTemplate({
    name: form.name,
    scope: form.scope,
    description: form.description,
    types: form.scope === 'ROUTE' ? [...ROUTE_DETECTIONS] : [...CHECKPOINT_DETECTIONS],
    prompts: {},
  })
  dialogVisible.value = false
  ElMessage.success('模板已创建')
}
</script>

<style scoped>
.info-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.7;
  margin: 0 0 12px;
}

.prompt {
  font-size: 12px;
  color: #909399;
}
</style>
