<template>
  <div class="login-page">
    <!-- 背景装饰光斑 -->
    <span class="blob blob-1"></span>
    <span class="blob blob-2"></span>
    <span class="blob blob-3"></span>

    <div class="login-box">
      <!-- 左侧：品牌介绍区 -->
      <aside class="brand">
        <div class="brand-logo">WM</div>
        <h1 class="brand-title">工作管理中台</h1>
        <p class="brand-sub">Vue 3 + Element Plus<br />企业级后台项目实战</p>
        <ul class="brand-list">
          <li>统一工作台 · 权限可控</li>
          <li>数据看板 · 一屏掌握</li>
          <li>用户管理 · 增删改查</li>
        </ul>
      </aside>

      <!-- 右侧：登录表单区 -->
      <section class="form-side">
        <h2 class="form-title">欢迎回来</h2>
        <p class="form-sub">请使用你的工作台账号登录</p>

        <el-form ref="loginFormRef" :model="loginForm" @submit.prevent>
          <el-form-item>
            <el-input
              v-model="loginForm.username"
              size="large"
              placeholder="请输入账号"
              clearable
              @keyup.enter="login"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item>
            <el-input
              v-model="loginForm.password"
              size="large"
              type="password"
              placeholder="请输入密码"
              show-password
              @keyup.enter="login"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" size="large" class="submit-btn" @click="login">
              登 录
            </el-button>
          </el-form-item>
        </el-form>

        <div class="demo-accounts">
          <p class="demo-title">演示账号（密码均为 123456）· 点击快速填充</p>
          <el-tag
            v-for="acc in demoAccounts"
            :key="acc.username"
            class="demo-tag"
            type="info"
            effect="plain"
            @click="fillAccount(acc)"
          >
            {{ acc.label }}
          </el-tag>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
export default {
  name: 'Login',
  data() {
    return {
      loginForm: {
        username: '',
        password: ''
      },
      demoAccounts: [
        { label: 'admin（管理员）', username: 'admin', password: '123456' },
        { label: 'manager（负责人）', username: 'manager', password: '123456' },
        { label: 'viewer（访客）', username: 'viewer', password: '123456' }
      ]
    }
  },
  methods: {
    fillAccount(acc) {
      this.loginForm.username = acc.username
      this.loginForm.password = acc.password
    },
    login() {
      // 三个演示账号：角色 + 按钮权限（第 9 课）
      const accounts = {
        admin: { role: 'admin', permissions: ['user:add', 'user:edit', 'user:delete'] },
        manager: { role: 'manager', permissions: ['user:edit'] },
        viewer: { role: 'viewer', permissions: [] }
      }
      const username = this.loginForm.username.trim()
      const account = accounts[username]
      if (!account || this.loginForm.password !== '123456') {
        this.$message.error('账号或密码错误')
        return
      }
      // 调用 vuex 保存登录信息
      this.$store.commit('setUserInfo', { username, ...account })
      this.$store.commit('setToken', `${username}-demo-token`)
      this.$message.success('登录成功')
      this.$router.push('/dashboard')
    }
  }
}
</script>

<style scoped>
/* ===== 页面容器：渐变底色 + 居中 ===== */
.login-page {
  position: relative;
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(135deg, #0f2557 0%, #1e46a3 45%, #3d7ef0 100%);
}

/* ===== 背景装饰光斑 ===== */
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  pointer-events: none;
}
.blob-1 { width: 420px; height: 420px; background: #4b8bf5; opacity: 0.55; top: -120px; left: -110px; }
.blob-2 { width: 360px; height: 360px; background: #7c5cff; opacity: 0.5; bottom: -150px; right: -90px; }
.blob-3 { width: 260px; height: 260px; background: #22d3ee; opacity: 0.28; bottom: 40px; left: 24%; }

/* ===== 登录卡片：左右分栏 ===== */
.login-box {
  position: relative;
  z-index: 1;
  display: flex;
  width: 860px;
  max-width: calc(100vw - 40px);
  min-height: 500px;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 24px 60px rgba(9, 24, 63, 0.35);
  overflow: hidden;
}

/* ===== 左侧品牌区 ===== */
.brand {
  position: relative;
  width: 340px;
  flex-shrink: 0;
  padding: 48px 36px;
  display: flex;
  flex-direction: column;
  color: #fff;
  overflow: hidden;
  background: linear-gradient(160deg, #1b3a8f 0%, #2f6bea 55%, #5b9bff 100%);
}
/* 装饰圆环 */
.brand::after {
  content: '';
  position: absolute;
  width: 260px;
  height: 260px;
  right: -110px;
  bottom: -110px;
  border: 40px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}
.brand-logo {
  width: 52px;
  height: 52px;
  margin-bottom: 26px;
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
}
.brand-title {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 0.5px;
  margin-bottom: 12px;
}
.brand-sub {
  font-size: 13px;
  line-height: 1.8;
  color: rgba(255, 255, 255, 0.82);
}
.brand-list {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding: 0;
  list-style: none;
}
.brand-list li {
  position: relative;
  padding-left: 20px;
  margin-bottom: 14px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.92);
}
.brand-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7dd3fc;
  box-shadow: 0 0 0 4px rgba(125, 211, 252, 0.22);
}

/* ===== 右侧表单区 ===== */
.form-side {
  flex: 1;
  padding: 54px 48px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}
.form-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
  margin-bottom: 8px;
}
.form-sub {
  font-size: 13px;
  color: #909399;
  margin-bottom: 28px;
}
.form-side :deep(.el-form-item) {
  margin-bottom: 20px;
}
.form-side :deep(.el-input__wrapper) {
  border-radius: 10px;
  padding: 4px 14px;
  box-shadow: 0 0 0 1px #e3e8ef inset;
  transition: box-shadow 0.2s;
}
.form-side :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #c8d3e0 inset;
}
.form-side :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #409eff inset;
}

/* 渐变主按钮 */
.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2f6bea, #5b9bff);
  box-shadow: 0 8px 20px rgba(47, 107, 234, 0.35);
  transition: transform 0.15s, box-shadow 0.15s;
}
.submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(47, 107, 234, 0.45);
  background: linear-gradient(135deg, #2f6bea, #5b9bff);
}
.submit-btn:active {
  transform: translateY(0);
}

/* 演示账号 */
.demo-accounts {
  margin-top: 4px;
  padding-top: 16px;
  border-top: 1px dashed #e6e6e6;
}
.demo-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 10px;
}
.demo-tag {
  margin: 0 8px 8px 0;
  cursor: pointer;
  transition: transform 0.15s, color 0.15s;
}
.demo-tag:hover {
  transform: translateY(-1px);
  color: #2f6bea;
  border-color: #2f6bea;
}

/* ===== 小屏适配：隐藏左侧品牌区 ===== */
@media (max-width: 820px) {
  .brand {
    display: none;
  }
  .login-box {
    width: 420px;
    min-height: auto;
  }
  .form-side {
    padding: 40px 30px;
  }
}
</style>
