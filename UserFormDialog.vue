<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="500px"
    @update:model-value="handleVisibleChange"
    @closed="handleClosed"
  >
    <el-form :model="form" label-width="80px">
      <el-form-item label="用户名">
        <el-input v-model="form.username"></el-input>
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="form.phone"></el-input>
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="form.role" style="width:100%">
          <el-option label="管理员" value="admin" />
          <el-option label="普通用户" value="user" />
        </el-select>
      </el-form-item>
      <!-- 第 8 课：本地头像预览（弹窗关闭时卸载以释放 Blob URL） -->
      <el-form-item label="头像预览">
        <AvatarUpload v-if="modelValue" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleVisibleChange(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script>
import AvatarUpload from './AvatarUpload.vue'

export default {
  name: 'UserFormDialog',
  components: { AvatarUpload },
  emits: ['update:modelValue', 'submit', 'closed'],
  // ① 接收父组件传下来的数据
  props: {
    // 控制显隐：v-model 的本质就是这个 modelValue
    modelValue: {
      type: Boolean,
      default: false
    },
    // 弹窗标题
    title: {
      type: String,
      default: '新增用户'
    },
    // 父组件传进来的"要编辑的那一行数据"
    rowData: {
      type: Object,
      default: () => ({})
    },
    // 提交按钮的 loading，由父组件控制
    submitting: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      // 组件内部的表单副本，避免直接修改 props 里的对象
      form: {
        id: '',
        username: '',
        phone: '',
        role: ''
      }
    }
  },
  watch: {
    // 监听 rowData 变化：父组件每次打开弹窗都会传新数据进来
    rowData: {
      immediate: true,
      handler(val) {
        this.form =
          val && val.id
            ? { ...val } // 有 id → 编辑
            : { id: '', username: '', phone: '', role: '' } // 无 id → 新增
      }
    }
  },
  methods: {
    // ② 显隐变化时，通知父组件更新 v-model
    handleVisibleChange(val) {
      this.$emit('update:modelValue', val)
    },
    // 关闭动画结束后，通知父组件"弹窗已关"
    handleClosed() {
      this.$emit('closed')
    },
    // 提交：把表单数据发给父组件，由父组件决定是新增还是编辑
    handleSubmit() {
      this.$emit('submit', { ...this.form })
    }
  }
}
</script>
