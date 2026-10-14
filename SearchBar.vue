<template>
  <div class="search-box">
    <el-input
      :model-value="modelValue"
      :placeholder="placeholder"
      style="width:220px"
      @update:model-value="$emit('update:modelValue', $event)"
      @keyup.enter="$emit('search')"
    />
    <el-button type="primary" @click="$emit('search')">查询</el-button>
    <!-- can-add 由父组件按权限传入（第 9 课） -->
    <el-button v-if="canAdd" type="success" @click="$emit('add')">新增</el-button>
  </div>
</template>

<script>
export default {
  name: 'SearchBar',
  props: {
    modelValue: { type: String, default: '' },
    placeholder: { type: String, default: '请输入关键字' },
    canAdd: { type: Boolean, default: true }
  },
  emits: ['update:modelValue', 'search', 'add']
}
</script>

<style scoped>
.search-box {
  display: flex;
  gap: 10px;
  align-items: center;
}
</style>
