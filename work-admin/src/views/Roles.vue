<template>
  <section>
    <h2>角色管理</h2>
    <el-space wrap>
      <el-input v-model="keyword" placeholder="筛选已加载的全部角色" />
      <el-button :disabled="busy" @click="load">刷新</el-button>
      <el-button v-if="can('role:create')" :disabled="busy" @click="open()">新增角色</el-button>
    </el-space>
    <el-table v-loading="busy" :data="filtered" row-key="id">
      <el-table-column prop="name" label="角色名" />
      <el-table-column prop="code" label="编码" />
      <el-table-column prop="userCount" label="绑定用户数" />
      <el-table-column label="状态"><template #default="{row}">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作" width="260"><template #default="{row}">
        <el-button v-if="can('role:update')" link :disabled="busy" @click="open(row)">编辑</el-button>
        <el-button v-if="can('role:grant')" link :disabled="busy || row.code==='super_admin'" @click="openGrant(row)">分配权限</el-button>
        <el-button v-if="can('role:delete')" link type="danger" :disabled="busy || row.builtin || row.userCount>0" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="角色资料" :show-close="!busy">
      <el-form label-width="100px" @submit.prevent>
        <el-form-item label="角色名"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="编码"><el-input v-model="form.code" :disabled="form.builtin" maxlength="64" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" maxlength="255" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :disabled="form.builtin" /></el-form-item>
      </el-form>
      <template #footer><el-button :loading="busy" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="grantVisible" :title="`权限：${grantRole?.name || ''}`" :show-close="!busy">
      <p>独立勾选节点；保存后后端会补齐按钮所属页面和上级目录。空选表示清空授权。</p>
      <el-tree ref="tree" :data="tree" node-key="id" :props="{label:'name',children:'children'}"
        show-checkbox check-strictly default-expand-all />
      <template #footer><el-button :loading="busy" @click="saveGrant">保存权限</el-button></template>
    </el-dialog>
  </section>
</template>
<script>
import { getRoles, createRole, updateRole, deleteRole, getRolePermissions, saveRolePermissions, getPermissionTree } from '../api/access'
import { toTree } from '../utils/menu'
export default {
  name: 'RoleManagement',
  data: () => ({ rows: [], keyword: '', busy: false, visible: false, form: {}, grantVisible: false, grantRole: null, tree: [] }),
  computed: { filtered() { const k=this.keyword.trim().toLowerCase(); return this.rows.filter(r=>`${r.name} ${r.code}`.toLowerCase().includes(k)) } },
  created() { this.load() },
  methods: {
    can(p) { return this.$store.getters.can(p) },
    async load() { const locked=this.busy; this.busy=true; try { this.rows=await getRoles() } catch {} finally { this.busy=locked } },
    open(row) { this.form=row ? {...row} : {name:'',code:'',description:'',enabled:true}; this.visible=true },
    async refreshAccess() {
      await this.$store.dispatch('refreshAccess')
      if (!this.can('role:read')) await this.$router.replace('/forbidden')
    },
    async save() {
      if(this.busy) return
      if(!this.form.name.trim() || !/^[a-zA-Z][a-zA-Z0-9_:-]{0,63}$/.test(this.form.code)) return this.$message.warning('填写角色名；编码以英文字母开头')
      this.busy=true
      try {
        const {name,code,description,enabled}=this.form
        if(this.form.id) await updateRole(this.form.id,{name,code,description,enabled})
        else await createRole({name,code,description,enabled})
        this.visible=false; await this.load(); await this.refreshAccess()
      } catch {} finally { this.busy=false }
    },
    async remove(row) {
      if(this.busy) return
      this.busy=true
      try { await this.$confirm(`删除角色 ${row.name}？`,'提示'); await deleteRole(row.id); await this.load() }
      catch {} finally { this.busy=false }
    },
    async openGrant(row) {
      if(this.busy) return
      this.busy=true
      try {
        const [nodes,ids]=await Promise.all([getPermissionTree(),getRolePermissions(row.id)])
        this.tree=toTree(nodes); this.grantRole=row; this.grantVisible=true
        await this.$nextTick(); this.$refs.tree.setCheckedKeys(ids)
      } catch {} finally { this.busy=false }
    },
    async saveGrant() {
      if(this.busy) return
      this.busy=true
      try {
        await saveRolePermissions(this.grantRole.id,this.$refs.tree.getCheckedKeys())
        this.grantVisible=false; await this.refreshAccess(); this.$message.success('授权已保存')
      } catch {} finally { this.busy=false }
    }
  }
}
</script>