<template>
  <div class="layout">
    <!-- 左侧侧边栏 -->
    <el-aside width="220px" class="aside">
      <div class="logo">
        <span>工作管理中台</span>
      </div>
      <!-- 菜单由路由 meta 动态派生（第 9 课） -->
      <el-menu
        :default-active="$route.path"
        router
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item v-for="item in menuList" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 右侧整体容器 -->
    <div class="main-wrap">
      <!-- 顶部导航栏 -->
      <el-header class="header">
        <div>欢迎，{{ $store.state.userInfo?.username }}</div>
        <el-button link @click="handleLogout">退出登录</el-button>
      </el-header>
      <!-- 主内容区域，子路由渲染在这里 -->
      <el-main class="content">
        <router-view />
      </el-main>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Layout',
  computed: {
    userRole() {
      return this.$store.state.userInfo?.role
    },
    // 从路由表派生菜单，避免再维护一份角色列表
    menuList() {
      return this.$router
        .getRoutes()
        .filter((route) => route.meta.menu)
        .filter((route) => !route.meta.roles || route.meta.roles.includes(this.userRole))
        .map((route) => ({
          path: route.path,
          title: route.meta.title,
          icon: route.meta.icon
        }))
    }
  },
  methods: {
    handleLogout() {
      // 清空登录信息，跳转到登录页
      this.$store.commit('clearUserInfo')
      this.$router.push('/login')
    }
  }
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  margin: 0;
  padding: 0;
  overflow: hidden;
}
.aside {
  background-color: #304156;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  border-bottom: 1px solid #434a50;
}
.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.header {
  background: #ffffff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
}
.content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}
</style>
