import { createRouter, createWebHashHistory } from 'vue-router'
import store from '../store'
import Layout from '../views/Layout.vue'
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue') },
    { path: '/register', component: () => import('../views/Register.vue') },
    { path: '/', component: Layout, redirect: '/dashboard', children: [
      { path: 'dashboard', component: () => import('../views/Dashboard.vue') },
      { path: 'user', component: () => import('../views/User.vue'), meta: { permission: 'user:read' } },
      { path: 'roles', component: () => import('../views/Roles.vue'), meta: { permission: 'role:read' } },
      { path: 'menus', component: () => import('../views/Menus.vue'), meta: { permission: 'menu:read' } },
      { path: 'messages', component: () => import('../views/Messages.vue'), meta: { permission: 'message:access' } },
      { path: 'chat', component: () => import('../views/Chat.vue'), meta: { permission: 'chat:access' } },
      { path: 'forbidden', component: () => import('../views/Forbidden.vue') }
    ] },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})
router.beforeEach(async to => {
  if (['/login','/register'].includes(to.path)) return true
  if (!store.state.token) return '/login'
  try {
    await store.dispatch('refreshAccess')
    if (to.meta.permission && !store.getters.can(to.meta.permission)) return '/forbidden'
  } catch {
    return store.state.token ? (to.path === '/forbidden' ? true : '/forbidden') : '/login'
  }
  return true
})
export default router