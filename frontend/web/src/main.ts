import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import 'leaflet/dist/leaflet.css'

import App from './App.vue'
import router from './router'
import { setupRouterGuards } from './router/guards'
import { useAuthStore } from './stores/auth'
import { loadAppData } from './stores/bootstrap'
import './style.css'

const app = createApp(App)
const pinia = createPinia()

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(pinia)
app.use(router)

const authStore = useAuthStore()
authStore.restoreSession()
if (authStore.isLoggedIn) {
  void loadAppData()
}

setupRouterGuards(router)

app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
