<template>
  <el-container class="layout">
    <el-aside width="220px">
      <h3>工作管理中台</h3>
      <el-menu router :default-active="$route.path">
        <el-menu-item index="/dashboard">仪表盘（教学静态数据）</el-menu-item>
        <NavBranch v-for="node in menus" :key="node.id" :node="node" />
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <span>欢迎，{{ user.name || user.account || '用户' }} · {{ $store.state.access.role?.name || '未分配角色' }}</span>
        <div><el-button :loading="refreshing" @click="refresh">刷新权限</el-button>
          <el-button :loading="leaving" @click="logout">退出登录</el-button></div>
      </el-header>
      <el-main><router-view :key="$store.state.token" /></el-main>
    </el-container>
  </el-container>
</template>
<script>
import { logoutApi } from '../api/auth'
import { navigation } from '../utils/menu'
import NavBranch from '../components/NavBranch.vue'
export default {
  name: 'AdminLayout', components: { NavBranch },
  data: () => ({ refreshing: false, leaving: false }),
  computed: {
    user() { return this.$store.state.userInfo || {} },
    menus() { return navigation(this.$store.state.access.menus) }
  },
  methods: {
    async refresh() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await this.$store.dispatch('refreshAccess')
        if (this.$route.meta.permission && !this.$store.getters.can(this.$route.meta.permission)) await this.$router.replace('/forbidden')
      } catch { /* 请求层提示错误。 */ }
      finally { this.refreshing = false }
    },
    async logout() {
      if (this.leaving) return
      this.leaving = true
      const token = this.$store.state.token
      try { await logoutApi() } catch { /* 无论网络结果如何，都退出本地会话。 */ }
      finally {
        if (token === this.$store.state.token) {
          this.$store.commit('clearUserInfo'); await this.$router.replace('/login')
        }
        this.leaving = false
      }
    }
  }
}
</script>
<style scoped>
.layout { min-height:100vh; } h3 { padding:16px; }
.el-header { display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #ddd; }
.el-aside { border-right:1px solid #ddd; } .el-main { background:#f6f8fb; }
</style>