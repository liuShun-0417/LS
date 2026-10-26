<template>
  <section>
    <h2>仪表盘</h2>
    <el-alert title="仪表盘为教学静态数据，暂无对应统计接口" :closable="false" />
    <el-row :gutter="16">
      <el-col :span="8" v-for="card in cards" :key="card.title">
        <el-card><template #header>{{ card.title }}</template><p class="num">{{ card.value }}</p></el-card>
      </el-col>
    </el-row>
  </section>
</template>
<script>
export default {
  name: 'DashboardView',
  data: () => ({ cards: [
    { title: '用户总数', value: '—' },
    { title: '角色数量', value: '—' },
    { title: '群组数量', value: '—' }
  ] })
}
</script>
<style scoped>.num { font-size: 28px; margin: 0; }</style>