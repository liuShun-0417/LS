<template>
  <section style="max-width:400px;margin:60px auto">
    <h2>注册普通用户</h2>
    <el-input v-model="form.name" maxlength="100" placeholder="姓名（选填）" />
    <el-input v-model="form.account" maxlength="100" placeholder="登录账号" />
    <el-input v-model="form.passwd" maxlength="100" type="password" show-password placeholder="登录密码" />
    <el-button type="primary" :loading="busy" @click="register">注册并登录</el-button>
    <router-link to="/login">返回登录</router-link>
  </section>
</template>
<script>
import { registerApi } from '../api/auth'
export default {
  name: 'AccountRegister',
  data: () => ({ form: {name:'',account:'',passwd:''}, busy:false }),
  methods: {
    async register() {
      if(this.busy) return
      if(!this.form.account.trim() || !this.form.passwd.trim()) return this.$message.warning('请填写账号和密码')
      this.busy=true
      try {
        const result=await registerApi({...this.form,account:this.form.account.trim()})
        this.$store.commit('setSession',result)
        await this.$router.replace('/dashboard')
      } catch(error) { this.$message.error(error.userMessage || '注册失败') }
      finally { this.busy=false }
    }
  }
}
</script>