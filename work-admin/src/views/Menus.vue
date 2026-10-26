<template>
  <section>
    <h2>菜单与按钮权限</h2>
    <el-space>
      <el-button :disabled="busy" @click="load">刷新</el-button>
      <el-button v-if="can('menu:create')" :disabled="busy" @click="open()">新增节点</el-button>
    </el-space>
    <el-table v-loading="busy" :data="tree" row-key="id" default-expand-all>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="type" label="类型" />
      <el-table-column prop="path" label="后端页面路径" />
      <el-table-column prop="permission" label="权限标识" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="150"><template #default="{row}">
        <el-button v-if="can('menu:update')" link :disabled="busy" @click="open(row)">编辑</el-button>
        <el-button v-if="can('menu:delete')" link type="danger" :disabled="busy || row.builtin || row.children.length>0" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="visible" title="菜单节点" :show-close="!busy">
      <el-form label-width="110px" @submit.prevent>
        <el-form-item label="名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="form.type" :disabled="form.builtin" @change="typeChanged">
          <el-option label="目录" value="DIRECTORY" /><el-option label="页面" value="MENU" /><el-option label="按钮" value="BUTTON" />
        </el-select></el-form-item>
        <el-form-item label="上级"><el-select v-model="form.parentId" clearable :disabled="form.builtin" placeholder="清空为根节点">
          <el-option v-for="m in parents" :key="m.id" :label="`${m.name}（${m.type}）`" :value="m.id" />
        </el-select></el-form-item>
        <el-form-item v-if="form.type==='MENU'" label="页面路径"><el-input v-model="form.path" :disabled="form.builtin" maxlength="200" placeholder="/example.html" /></el-form-item>
        <el-form-item v-if="form.type!=='DIRECTORY'" label="权限标识"><el-input v-model="form.permission" :disabled="form.builtin" maxlength="100" placeholder="example:read" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" :max="9999" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" :disabled="form.builtin" /></el-form-item>
      </el-form>
      <p>内置节点只支持名称、排序。新增业务菜单后仍需编写组件、映射路由和后端接口。</p>
      <template #footer><el-button :loading="busy" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>
<script>
import { getMenus, createMenu, updateMenu, deleteMenu } from '../api/access'
import { toTree } from '../utils/menu'
export default {
  name: 'MenuManagement',
  data: () => ({ rows: [], busy: false, visible: false, form: {} }),
  computed: {
    tree() { return toTree(this.rows) },
    parents() {
      const forbidden=new Set(this.form.id ? [this.form.id] : [])
      let previous=-1
      while(previous!==forbidden.size) {
        previous=forbidden.size
        this.rows.forEach(m=>{ if(forbidden.has(m.parentId)) forbidden.add(m.id) })
      }
      const type=this.form.type==='BUTTON' ? 'MENU' : 'DIRECTORY'
      return this.rows.filter(m=>m.type===type && !forbidden.has(m.id))
    }
  },
  created() { this.load() },
  methods: {
    can(p) { return this.$store.getters.can(p) },
    async load() { const locked=this.busy; this.busy=true; try { this.rows=await getMenus() } catch {} finally { this.busy=locked } },
    open(row) {
      this.form=row ? {...row} : {name:'',type:'DIRECTORY',parentId:null,path:null,permission:null,sortOrder:0,enabled:true}
      this.visible=true
    },
    typeChanged() { this.form.parentId=null; this.form.path=null; this.form.permission=null },
    async changed() {
      this.visible=false; await this.load(); await this.$store.dispatch('refreshAccess')
      if(!this.can('menu:read')) await this.$router.replace('/forbidden')
    },
    async save() {
      if(this.busy) return
      if(!this.form.name.trim()) return this.$message.warning('请输入节点名称')
      this.busy=true
      try {
        const {name,type,sortOrder,enabled}=this.form
        const body={name,type,sortOrder,enabled,parentId:this.form.parentId || null,
          path:type==='MENU' ? this.form.path : null,
          permission:type==='DIRECTORY' ? null : this.form.permission}
        if(this.form.id) await updateMenu(this.form.id,body); else await createMenu(body)
        await this.changed()
      } catch {} finally { this.busy=false }
    },
    async remove(row) {
      if(this.busy) return
      this.busy=true
      try { await this.$confirm(`删除 ${row.name}？关联授权也会清理。`,'提示'); await deleteMenu(row.id); await this.changed() }
      catch {} finally { this.busy=false }
    }
  }
}
</script>