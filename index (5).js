import { createRouter, createWebHashHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import store from '../store'
import Layout from '../views/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    // 第 10 课：路由懒加载
    component: () => import('../views/Login.vue')
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '仪表盘', icon: 'House', menu: true, roles: ['admin', 'manager', 'viewer'] }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('../views/User.vue'),
        meta: { title: '用户管理', icon: 'User', menu: true, roles: ['admin', 'manager'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

// 全局路由守卫：登录校验 + 角色校验（第 3 / 9 课）
router.beforeEach((to) => {
  const token = store.state.token

  // 已登录访问登录页 -> 送回首页，避免重复登录
  if (to.path === '/login') {
    return token ? '/dashboard' : true
  }

  // 未登录 -> 一律去登录页
  if (!token) {
    return '/login'
  }

  // 已登录：校验当前角色是否有权访问目标页面
  const role = store.state.userInfo?.role
  const allowed = to.meta.roles
  if (Array.isArray(allowed) && !allowed.includes(role)) {
    ElMessage.warning('您没有权限访问该页面')
    return '/dashboard'
  }

  return true
})

export default router
