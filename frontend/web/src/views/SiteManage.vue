<template>
  <div>
    <PageHeader title="站点与区域管理" description="变电站站点信息与区域划分" :breadcrumbs="[{ label: '巡检业务' }, { label: '站点管理' }]">
      <template #actions>
        <el-button v-if="can('site:edit')" type="primary" @click="openSiteDialog()">
          <el-icon><Plus /></el-icon>
          新建站点
        </el-button>
      </template>
    </PageHeader>

    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>站点列表</template>
          <el-table
            :data="siteStore.sites"
            highlight-current-row
            @current-change="onSiteSelect"
            size="small"
          >
            <el-table-column prop="name" label="站点名称" />
            <el-table-column prop="address" label="地址" show-overflow-tooltip />
            <el-table-column label="LingBot-Map" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.lingbotMapId" type="success" size="small">已关联</el-tag>
                <el-tag v-else type="info" size="small">未建图</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button v-if="can('site:edit')" text type="primary" size="small" @click.stop="openSiteDialog(row)">编辑</el-button>
                <el-button v-if="can('site:edit')" text type="danger" size="small" @click.stop="removeSite(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="14">
        <el-card shadow="never" v-if="currentSite">
          <template #header>
            <div class="card-head">
              <span>{{ currentSite.name }} · 区域划分</span>
              <el-button v-if="can('site:edit')" size="small" type="primary" @click="openAreaDialog()">添加区域</el-button>
            </div>
          </template>
          <el-table :data="areas" size="small">
            <el-table-column prop="name" label="区域名称" />
            <el-table-column label="顶点数" width="80">
              <template #default="{ row }">{{ row.polygon.length }}</template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="danger" size="small" @click="siteStore.removeArea(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div style="height: 320px; margin-top: 12px">
            <Map2D :center="currentSite.center" :areas="areas" />
          </div>
        </el-card>
        <el-card v-else shadow="never">
          <div class="empty-hint">请选择左侧站点查看区域</div>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="siteDialogVisible" :title="editingSite ? '编辑站点' : '新建站点'" width="480px">
      <el-form :model="siteForm" label-width="90px">
        <el-form-item label="站点名称" required>
          <el-input v-model="siteForm.name" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="siteForm.address" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="siteForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="中心纬度">
          <el-input-number v-model="siteForm.center.lat" :step="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="中心经度">
          <el-input-number v-model="siteForm.center.lng" :step="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="LingBot ID">
          <el-input v-model="siteForm.lingbotMapId" placeholder="三维建图 ID（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="siteDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSite">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="areaDialogVisible" title="添加区域" width="480px">
      <el-form :model="areaForm" label-width="90px">
        <el-form-item label="区域名称" required>
          <el-input v-model="areaForm.name" />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="演示版使用默认矩形区域，完整版可在地图上绘制多边形"
          style="margin-bottom: 12px"
        />
      </el-form>
      <template #footer>
        <el-button @click="areaDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveArea">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Map2D from '@/components/Map2D.vue'
import PageHeader from '@/components/PageHeader.vue'
import { usePermission } from '@/composables/usePermission'
import { useSiteStore } from '@/stores/site'
import type { Site } from '@/types'

const siteStore = useSiteStore()
const { can } = usePermission()
const currentSite = ref<Site | null>(siteStore.sites[0] ?? null)
const siteDialogVisible = ref(false)
const areaDialogVisible = ref(false)
const editingSite = ref<Site | null>(null)

const siteForm = reactive({
  name: '',
  address: '',
  description: '',
  center: { lat: 30.2741, lng: 120.1551 },
  lingbotMapId: '',
})

const areaForm = reactive({ name: '' })

const areas = computed(() =>
  currentSite.value ? siteStore.getAreasBySite(currentSite.value.id) : [],
)

function onSiteSelect(site: Site | undefined) {
  currentSite.value = site ?? null
}

function openSiteDialog(site?: Site) {
  editingSite.value = site ?? null
  if (site) {
    Object.assign(siteForm, {
      name: site.name,
      address: site.address,
      description: site.description,
      center: { ...site.center },
      lingbotMapId: site.lingbotMapId ?? '',
    })
  } else {
    Object.assign(siteForm, {
      name: '',
      address: '',
      description: '',
      center: { lat: 30.2741, lng: 120.1551 },
      lingbotMapId: '',
    })
  }
  siteDialogVisible.value = true
}

function saveSite() {
  if (!siteForm.name.trim()) {
    ElMessage.warning('请填写站点名称')
    return
  }
  if (editingSite.value) {
    siteStore.updateSite(editingSite.value.id, {
      name: siteForm.name,
      address: siteForm.address,
      description: siteForm.description,
      center: { ...siteForm.center },
      lingbotMapId: siteForm.lingbotMapId || undefined,
    })
    ElMessage.success('站点已更新')
  } else {
    const site = siteStore.addSite({
      name: siteForm.name,
      address: siteForm.address,
      description: siteForm.description,
      center: { ...siteForm.center },
      lingbotMapId: siteForm.lingbotMapId || undefined,
    })
    currentSite.value = site
    ElMessage.success('站点已创建')
  }
  siteDialogVisible.value = false
}

function removeSite(id: string) {
  ElMessageBox.confirm('删除站点将同时删除其区域数据，是否继续？', '确认', { type: 'warning' })
    .then(() => {
      siteStore.removeSite(id)
      if (currentSite.value?.id === id) currentSite.value = siteStore.sites[0] ?? null
      ElMessage.success('已删除')
    })
    .catch(() => {})
}

function openAreaDialog() {
  areaForm.name = ''
  areaDialogVisible.value = true
}

function saveArea() {
  if (!currentSite.value || !areaForm.name.trim()) {
    ElMessage.warning('请填写区域名称')
    return
  }
  const c = currentSite.value.center
  const d = 0.0004
  siteStore.addArea({
    siteId: currentSite.value.id,
    name: areaForm.name,
    polygon: [
      { lat: c.lat + d, lng: c.lng - d },
      { lat: c.lat + d, lng: c.lng + d },
      { lat: c.lat - d, lng: c.lng + d },
      { lat: c.lat - d, lng: c.lng - d },
    ],
  })
  areaDialogVisible.value = false
  ElMessage.success('区域已添加')
}
</script>

<style scoped>
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
