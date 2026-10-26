<template>
  <section>
    <h2>用户管理</h2>
    <el-space wrap>
      <el-input v-model="keyword" maxlength="100" placeholder="搜索全部用户的姓名或账号" clearable style="width:260px" @keyup.enter="handleSearch" />
      <el-button type="primary" :disabled="busy" @click="handleSearch">搜索</el-button>
      <el-button :disabled="busy" @click="handleResetSearch">重置</el-button>
      <el-button v-if="can('user:create')" :disabled="busy" @click="openDialog()">新增用户</el-button>
    </el-space>
    <p class="hint">每页 20 条；搜索、总数和分页均由服务器返回</p>
    <el-table v-loading="loading" :data="items">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="姓名" min-width="120" />
      <el-table-column prop="account" label="账号" min-width="140" />
      <el-table-column prop="roleName" label="角色" min-width="130" />
      <el-table-column label="操作" min-width="260"><template #default="{ row }">
        <el-button v-if="can('user:update')" link :disabled="busy" @click="openDialog(row)">编辑</el-button>
        <el-button v-if="can('user:assign-role')" link :disabled="busy" @click="openRole(row)">绑定角色</el-button>
        <el-button v-if="can('user:delete')" link type="danger" :disabled="busy" @click="remove(row)">删除</el-button>
      </template></el-table-column>
      <template #empty><span>没有符合条件的用户</span></template>
    </el-table>
    <el-pagination background layout="prev, pager, next, total" :total="total" :page-size="20" :current-page="page" @current-change="handlePageChange" />

    <el-dialog v-model="dialogVisible" :title="dialogTitle" :show-close="!busy">
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="姓名"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="账号"><el-input v-model="form.account" maxlength="100" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.passwd" type="password" show-password maxlength="100" :placeholder="form.id ? '留空保持原密码' : '必填'" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="busy" @click="dialogVisible=false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="替换用户角色"
      :show-close="!roleSaving" :close-on-click-modal="!roleSaving"
      :close-on-press-escape="!roleSaving">
      <p>账号：{{ roleUser?.account }}</p>
      <el-select v-model="roleId" :disabled="roleSaving" placeholder="选择角色">
        <el-option v-for="role in roleOptions" :key="role.id"
          :label="role.name" :value="role.id" />
      </el-select>
      <template #footer>
        <el-button :disabled="roleSaving" @click="roleVisible=false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="saveRole">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
<script>
import { getUserList, createUser, updateUser, deleteUser } from '../api/user'
import { getRoleOptions, bindUserRole } from '../api/access'
export default {
  name: 'UserManagement',
  data: () => ({
    page: 1, keyword: '', appliedKeyword: '', items: [], total: 0,
    loading: false, dialogVisible: false, form: {},
    roleOptions: [], roleVisible: false, roleUser: null, roleId: null, roleSaving: false
  }),
  computed: {
    busy() { return this.loading || this.roleSaving },
    dialogTitle() { return this.form.id ? '编辑用户' : '新增用户' }
  },
  created() { this.loadUsers() },
  methods: {
    can(permission) { return this.$store.getters.can(permission) },
    async loadUsers(page = this.page) {
      this.loading = true
      try {
        let result = await getUserList(page, this.appliedKeyword)
        const lastPage = Math.max(1, result.totalPages)
        if (page > lastPage) result = await getUserList(lastPage, this.appliedKeyword)
        this.items = result.items
        this.page = result.page
        this.total = result.total
        return true
      } catch { return false } finally { this.loading = false }
    },
    handleSearch() { if (this.busy) return; this.appliedKeyword = this.keyword.trim(); this.loadUsers(1) },
    resetSearch() { this.keyword = ''; this.appliedKeyword = '' },
    handleResetSearch() { if (this.busy) return; this.resetSearch(); this.loadUsers(1) },
    handlePageChange(page) { if (this.busy) return; this.loadUsers(page) },
    openDialog(row) {
      this.form = row
        ? { id: row.id, name: row.name || '', account: row.account, passwd: '' }
        : { name: '', account: '', passwd: '' }
      this.dialogVisible = true
    },
    async save() {
      if (this.busy) return
      if (!this.form.account || !this.form.account.trim()) return this.$message.warning('账号必填')
      if (!this.form.id && !(this.form.passwd || '').trim()) return this.$message.warning('新增时密码必填')
      this.loading = true
      try {
        const body = { name: this.form.name || null, account: this.form.account.trim(), passwd: this.form.passwd || null }
        if (this.form.id) await updateUser(this.form.id, body)
        else await createUser(body)
        this.dialogVisible = false
        await this.loadUsers()
      } catch { /* 请求层提示错误。 */ } finally { this.loading = false }
    },
    async remove(row) {
      if (this.busy) return
      this.loading = true
      try { await this.$confirm(`删除用户 ${row.account}？`, '提示'); await deleteUser(row.id); await this.loadUsers() }
      catch { /* 取消或失败。 */ } finally { this.loading = false }
    },
    async openRole(row) {
      if (this.busy) return
      this.loading = true
      try {
        this.roleOptions = await getRoleOptions()
        this.roleUser = row
        this.roleId = this.roleOptions.some(r => r.id === row.roleId)
          ? row.roleId : null
        this.roleVisible = true
      } catch {
        // 请求层显示错误。
      } finally {
        this.loading = false
      }
    },
    async saveRole() {
      if (this.busy || !this.roleId) return
      this.roleSaving = true
      try {
        const userId = this.roleUser.id
        await bindUserRole(userId, this.roleId)
        this.roleVisible = false
        this.$message.success('角色已更新')
        if (userId === this.$store.state.userInfo?.id) {
          await this.$store.dispatch('refreshAccess')
          if (!this.can('user:read')) {
            await this.$router.replace('/forbidden')
            return
          }
        }
        await this.loadUsers()
      } catch {
        // 失败保留弹窗；不显示成功。
      } finally {
        this.roleSaving = false
      }
    }
  }
}
</script>
<style scoped>.hint { color: #888; font-size: 12px; }</style>