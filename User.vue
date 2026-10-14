<template>
  <el-card>
    <!-- 搜索栏（封装组件） -->
    <SearchBar
      v-model="query.keyword"
      placeholder="搜索用户名"
      :can-add="can('user:add')"
      @search="handleSearch"
      @add="openAddDialog"
    />

    <!-- 批量删除（第 8 课） -->
    <div style="margin-top:15px">
      <el-button
        v-if="can('user:delete')"
        type="danger"
        :loading="deleting"
        :disabled="selectedRows.length === 0"
        @click="handleBatchDelete"
      >
        批量删除（已选 {{ selectedRows.length }} 项）
      </el-button>
    </div>

    <!-- 用户表格 -->
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="tableData"
      border
      stripe
      style="margin-top:15px"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column label="ID" prop="id" width="80" />
      <el-table-column label="用户名" prop="username" />
      <el-table-column label="手机号" prop="phone" />
      <el-table-column label="角色" prop="role">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : 'info'">
            {{ row.role === 'admin' ? '管理员' : '普通用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-if="can('user:edit')" link type="primary" @click="openEditDialog(row)">
            编辑
          </el-button>
          <el-button v-if="can('user:delete')" link type="danger" @click="handleDelete(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 封装好的弹窗表单组件 -->
    <UserFormDialog
      v-model="dialogVisible"
      :title="dialogTitle"
      :row-data="currentRow"
      :submitting="submitting"
      @submit="submitForm"
    />
  </el-card>
</template>

<script>
// 引入接口
import { getUserList, addUser, editUser, delUser } from '../api/user'
import UserFormDialog from '../components/UserFormDialog.vue'
import SearchBar from '../components/SearchBar.vue'

export default {
  name: 'User',
  components: { UserFormDialog, SearchBar },
  data() {
    return {
      query: { keyword: '' },
      tableData: [],
      loading: false, // 表格加载状态
      submitting: false, // 提交按钮加载状态
      deleting: false, // 批量删除加载状态
      selectedRows: [], // 表格勾选的行
      dialogVisible: false,
      dialogTitle: '新增用户',
      currentRow: {} // 传给弹窗的那一行数据
    }
  },
  created() {
    // 页面加载即拉取列表
    this.fetchList()
  },
  methods: {
    // 按钮权限判断（第 9 课）
    can(permission) {
      return this.$store.state.userInfo?.permissions?.includes(permission) || false
    },

    // ========== 统一的获取列表方法 ==========
    async fetchList() {
      this.loading = true
      try {
        const res = await getUserList({ keyword: this.query.keyword })
        if (res.code === 200) {
          this.tableData = res.data
        }
      } catch (e) {
        this.$message.error('获取列表失败')
        console.error(e)
      } finally {
        this.loading = false // 无论成功失败都要关掉 loading
      }
    },

    // 搜索：直接重新请求接口
    handleSearch() {
      this.fetchList()
    },

    // 打开新增弹窗：currentRow 传空对象
    openAddDialog() {
      this.dialogTitle = '新增用户'
      this.currentRow = {}
      this.dialogVisible = true
    },

    // 打开编辑弹窗：currentRow 传该行数据副本
    openEditDialog(row) {
      this.dialogTitle = '编辑用户'
      this.currentRow = { ...row }
      this.dialogVisible = true
    },

    // 接收弹窗抛上来的表单数据（新增 / 编辑分流）
    async submitForm(form) {
      this.submitting = true
      try {
        const res = form.id ? await editUser(form) : await addUser(form)
        if (res.code === 200) {
          this.$message.success(res.msg || '保存成功')
          this.dialogVisible = false
          this.fetchList()
        }
      } catch (e) {
        this.$message.error('保存失败')
      } finally {
        this.submitting = false
      }
    },

    // 单行删除
    async handleDelete(row) {
      try {
        await this.$confirm(`确定删除用户「${row.username}」吗？`, '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return // 用户取消
      }
      try {
        await delUser(row.id)
        this.$message.success('删除成功')
        await this.fetchList()
        this.$refs.tableRef.clearSelection()
      } catch (error) {
        this.$message.error(error.message || '删除失败')
      }
    },

    // 表格勾选变化
    handleSelectionChange(rows) {
      this.selectedRows = rows
    },

    // 批量删除
    async handleBatchDelete() {
      const ids = this.selectedRows.map((row) => row.id)
      if (ids.length === 0) return

      try {
        await this.$confirm(`确定删除选中的 ${ids.length} 个用户吗？`, '批量删除', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
      } catch {
        return // 用户取消
      }

      this.deleting = true
      try {
        for (const id of ids) await delUser(id)
        this.$message.success(`成功删除 ${ids.length} 条数据`)
      } catch (error) {
        this.$message.error(`删除中断：${error.message || '请求失败'}`)
      } finally {
        await this.fetchList()
        this.$refs.tableRef.clearSelection()
        this.selectedRows = []
        this.deleting = false
      }
    }
  }
}
</script>
