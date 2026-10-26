import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import store, { installSessionSync } from './store'
import { setUnauthorizedHandler } from './api/request'

installSessionSync(store)
setUnauthorizedHandler(() => {
  if (router.currentRoute.value.path !== '/login') router.replace('/login')
})

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus)
app.use(store)
app.use(router)
app.mount('#app')