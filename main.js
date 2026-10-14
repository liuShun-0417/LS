import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { House, User, Plus } from '@element-plus/icons-vue'

// 引入 Mock：拦截匹配的请求，零后端联调（对接真实后端时删除这一行）
import './mock'

const app = createApp(App)

// 只注册课程用到的三个图标：仪表盘、用户管理、头像预览
app.component('House', House)
app.component('User', User)
app.component('Plus', Plus)

app.use(store).use(router).use(ElementPlus).mount('#app')
