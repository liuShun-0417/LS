<template>
  <section class="login">
    <h2>登录</h2>
    <el-form label-width="80px" @submit.prevent>
      <el-form-item label="账号"><el-input v-model="form.account" maxlength="100" placeholder="登录账号" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.passwd" type="password" show-password maxlength="100" placeholder="登录密码" @keyup.enter="login" /></el-form-item>
      <el-button type="primary" :loading="busy" @click="login">登录</el-button>
    </el-form>
    <p class="hint">登录状态保存在当前浏览器</p>
    <router-link to="/register">注册新账号</router-link>
  </section>
</template>
<script>
import { loginApi } from '../api/auth'
export default {
  name: 'AccountLogin',
  data: () => ({ form: { account: '', passwd: '' }, busy: false }),
  methods: {
    async login() {
      if (this.busy) return
      if (!this.form.account.trim() || !this.form.passwd.trim()) return this.$message.warning('请输入账号和密码')
      this.busy = true
      try {
        const result = await loginApi({ account: this.form.account.trim(), passwd: this.form.passwd })
        this.$store.commit('setSession', result)
        await this.$router.replace('/dashboard')
      } catch (error) {
        this.$message.error(error.userMessage || '登录失败')
      } finally {
        this.busy = false
      }
    }
  }
}
</script>
<style scoped>
.login { max-width: 400px; margin: 80px auto; }
.hint { color: #888; font-size: 12px; }
</style>