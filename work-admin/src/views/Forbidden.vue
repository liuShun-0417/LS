<template>
  <el-result icon="warning" title="页面暂时无法访问" sub-title="可能未分配权限，或权限接口暂时不可用。可刷新权限后重试。">
    <template #extra><el-button @click="$router.push('/dashboard')">返回仪表盘</el-button></template>
  </el-result>
</template>
<script>export default { name: 'AccessForbidden' }</script>