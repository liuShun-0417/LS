<template>
  <el-sub-menu v-if="node.type === 'DIRECTORY'" :index="`dir-${node.id}`">
    <template #title>{{ node.name }}</template>
    <NavBranch v-for="child in node.children" :key="child.id" :node="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="node.route">{{ node.name }}</el-menu-item>
</template>
<script>
export default { name: 'NavBranch', props: { node: { type: Object, required: true } } }
</script>