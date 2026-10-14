<template>
  <div class="dashboard">
    <!-- ================= 顶部欢迎栏 ================= -->
    <div class="hero">
      <div class="hero-main">
        <h2 class="hero-title">
          工作台
          <el-tag v-if="overview.unreadMessages" class="hero-tag" type="danger" effect="dark" size="small" round>
            {{ overview.unreadMessages }} 条未读消息
          </el-tag>
        </h2>
        <p class="hero-sub">欢迎回来，今天是 {{ todayText }}，祝你工作顺利</p>
      </div>
      <div class="hero-actions">
        <span class="update-time">
          <el-icon><Clock /></el-icon>
          数据更新于 {{ updatedAt }}
        </span>
        <el-button type="primary" :loading="refreshing" @click="handleRefresh">
          <el-icon v-if="!refreshing" style="margin-right: 4px"><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- ================= 核心指标卡 ================= -->
    <el-row :gutter="16">
      <el-col v-for="card in statCards" :key="card.key" :xs="24" :sm="12" :md="6">
        <div class="stat-card" :style="{ '--accent': card.color }">
          <div class="stat-icon" :style="{ background: card.bg, color: card.color }">
            <el-icon :size="22"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-body">
            <div class="stat-label">{{ card.label }}</div>
            <div class="stat-value">
              {{ card.value }}<span class="stat-unit">{{ card.unit }}</span>
            </div>
            <div class="stat-foot">
              <span v-for="f in card.foot" :key="f.label" class="foot-item">
                {{ f.label }} <b :style="{ color: card.color }">{{ f.value }}</b>
              </span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ================= 图表区 一 ================= -->
    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><TrendCharts /></el-icon>近 7 天数据趋势</span>
              <span class="panel-tip">访问量 · 新增用户 · 完成任务</span>
            </div>
          </template>
          <div ref="trendRef" class="chart" style="height: 320px"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><PieChart /></el-icon>任务状态分布</span>
            </div>
          </template>
          <div class="chart-wrap">
            <div ref="statusRef" style="height: 320px"></div>
            <!-- 环形图中心文案：用 HTML 叠加最稳定 -->
            <div class="donut-center">
              <div class="donut-value">{{ overview.completionRate }}%</div>
              <div class="donut-label">任务完成率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ================= 图表区 二 ================= -->
    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><DataAnalysis /></el-icon>部门任务进度</span>
              <span class="panel-tip">共 {{ departments.length }} 个部门</span>
            </div>
          </template>
          <div ref="deptRef" class="chart" style="height: 300px"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><User /></el-icon>用户角色分布</span>
              <span class="panel-tip">共 {{ overview.totalUsers }} 人</span>
            </div>
          </template>
          <div ref="roleRef" class="chart" style="height: 300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ================= 最近任务 + 最新动态 ================= -->
    <el-row :gutter="16" class="mt16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><List /></el-icon>最近任务</span>
              <span class="panel-tip">共 {{ recentTasks.length }} 条</span>
            </div>
          </template>
          <el-table :data="recentTasks" style="width: 100%">
            <el-table-column prop="id" label="编号" width="92" />
            <el-table-column prop="title" label="任务标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="owner" label="负责人" width="88" />
            <el-table-column label="优先级" width="88">
              <template #default="{ row }">
                <el-tag :type="priorityType(row.priority)" effect="light" size="small">{{ row.priority }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="plain" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="截止日期" width="176">
              <template #default="{ row }">
                <span :class="['due', dueState(row).type]">{{ dueState(row).text }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel">
          <template #header>
            <div class="panel-head">
              <span class="panel-title"><el-icon><Bell /></el-icon>最新动态</span>
            </div>
          </template>
          <el-timeline class="act-timeline">
            <el-timeline-item
              v-for="(a, i) in activities"
              :key="i"
              :timestamp="a.time"
              placement="top"
              :type="i === 0 ? 'primary' : ''"
              :hollow="i !== 0"
            >
              <span class="act-user">{{ a.user }}</span>
              <span class="act-text">{{ a.action }}</span>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- ================= 系统公告 ================= -->
    <el-card shadow="never" class="panel mt16">
      <template #header>
        <div class="panel-head">
          <span class="panel-title"><el-icon><Warning /></el-icon>系统公告</span>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col v-for="an in announcements" :key="an.id" :xs="24" :md="8">
          <div class="notice" :class="{ 'is-important': an.level === '重要' }">
            <div class="notice-top">
              <el-tag :type="an.level === '重要' ? 'danger' : 'info'" size="small" effect="light">
                {{ an.level }}
              </el-tag>
              <span class="notice-date">{{ an.date }}</span>
            </div>
            <div class="notice-title">{{ an.title }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import {
  User,
  Tickets,
  Loading,
  CircleCheck,
  Clock,
  Refresh,
  TrendCharts,
  DataAnalysis,
  PieChart,
  List,
  Bell,
  Warning
} from '@element-plus/icons-vue'

export default {
  name: 'WorkDashboard',
  components: {
    User,
    Tickets,
    Loading,
    CircleCheck,
    Clock,
    Refresh,
    TrendCharts,
    DataAnalysis,
    PieChart,
    List,
    Bell,
    Warning
  },
  data() {
    return {
      refreshing: false,
      updatedAt: '2026-10-10 14:30',

      overview: {
        totalUsers: 136,
        activeUsers: 104,
        onlineUsers: 28,
        todayNewUsers: 9,
        totalTasks: 562,
        pendingTasks: 18,
        inProgressTasks: 123,
        completedTasks: 421,
        overdueTasks: 7,
        completionRate: 74.9,
        unreadMessages: 6
      },

      weeklyTrend: [
        { date: '10-04', visits: 182, newUsers: 4, completedTasks: 12 },
        { date: '10-05', visits: 196, newUsers: 5, completedTasks: 16 },
        { date: '10-06', visits: 205, newUsers: 3, completedTasks: 14 },
        { date: '10-07', visits: 231, newUsers: 7, completedTasks: 20 },
        { date: '10-08', visits: 224, newUsers: 6, completedTasks: 18 },
        { date: '10-09', visits: 260, newUsers: 8, completedTasks: 22 },
        { date: '10-10', visits: 278, newUsers: 9, completedTasks: 24 }
      ],

      departments: [
        { name: '运营部', inProgress: 38, completed: 118 },
        { name: '产品部', inProgress: 31, completed: 91 },
        { name: '技术部', inProgress: 28, completed: 83 },
        { name: '客服部', inProgress: 24, completed: 70 },
        { name: '财务部', inProgress: 20, completed: 59 }
      ],

      userRoles: [
        { name: '管理员', value: 4 },
        { name: '部门经理', value: 22 },
        { name: '普通成员', value: 110 }
      ],

      recentTasks: [
        { id: 'T-1058', title: '整理第三季度运营报告', owner: '张敏', priority: '高', status: '进行中', dueDate: '2026-10-11' },
        { id: 'T-1057', title: '审核新用户权限申请', owner: '李浩', priority: '高', status: '待处理', dueDate: '2026-10-10' },
        { id: 'T-1056', title: '更新产品帮助文档', owner: '王倩', priority: '中', status: '进行中', dueDate: '2026-10-13' },
        { id: 'T-1055', title: '修复工单筛选异常', owner: '陈卓', priority: '高', status: '已完成', dueDate: '2026-10-09' },
        { id: 'T-1054', title: '核对九月报销数据', owner: '赵琳', priority: '中', status: '待处理', dueDate: '2026-10-12' },
        { id: 'T-1053', title: '完成客户回访记录', owner: '刘洋', priority: '低', status: '已完成', dueDate: '2026-10-08' }
      ],

      activities: [
        { time: '14:18', user: '陈卓', action: '完成了任务「修复工单筛选异常」' },
        { time: '13:42', user: '李浩', action: '提交了 2 条权限申请' },
        { time: '11:26', user: '张敏', action: '更新了第三季度运营报告' },
        { time: '10:15', user: '王倩', action: '新增了产品帮助文档' }
      ],

      announcements: [
        { id: 1, title: '周例会安排', date: '2026-10-10', level: '重要' },
        { id: 2, title: '十月系统维护通知', date: '2026-10-09', level: '普通' },
        { id: 3, title: '第三季度数据归档提醒', date: '2026-10-08', level: '普通' }
      ]
    }
  },
  computed: {
    // 中文日期 + 星期
    todayText() {
      const d = new Date()
      const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][d.getDay()]
      return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 ${week}`
    },
    // 顶部四张核心指标卡（全部来自 overview）
    statCards() {
      const o = this.overview
      return [
        {
          key: 'users',
          label: '用户总数',
          value: o.totalUsers,
          unit: '人',
          icon: 'User',
          color: '#409EFF',
          bg: 'rgba(64, 158, 255, 0.12)',
          foot: [
            { label: '活跃', value: o.activeUsers },
            { label: '今日新增', value: '+' + o.todayNewUsers }
          ]
        },
        {
          key: 'tasks',
          label: '任务总数',
          value: o.totalTasks,
          unit: '个',
          icon: 'Tickets',
          color: '#7C5CFC',
          bg: 'rgba(124, 92, 252, 0.12)',
          foot: [
            { label: '完成率', value: o.completionRate + '%' },
            { label: '在线', value: o.onlineUsers }
          ]
        },
        {
          key: 'doing',
          label: '进行中任务',
          value: o.inProgressTasks,
          unit: '个',
          icon: 'Loading',
          color: '#E6A23C',
          bg: 'rgba(230, 162, 60, 0.12)',
          foot: [
            { label: '待处理', value: o.pendingTasks },
            { label: '逾期', value: o.overdueTasks }
          ]
        },
        {
          key: 'done',
          label: '已完成任务',
          value: o.completedTasks,
          unit: '个',
          icon: 'CircleCheck',
          color: '#67C23A',
          bg: 'rgba(103, 194, 58, 0.12)',
          foot: [
            { label: '未读消息', value: o.unreadMessages },
            { label: '在线', value: o.onlineUsers }
          ]
        }
      ]
    }
  },
  mounted() {
    // DOM 渲染完成后再初始化图表
    this.renderCharts()
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    // 销毁实例，防止内存泄漏
    this.trendChart && this.trendChart.dispose()
    this.statusChart && this.statusChart.dispose()
    this.deptChart && this.deptChart.dispose()
    this.roleChart && this.roleChart.dispose()
  },
  methods: {
    /* ---------------- 图表 ---------------- */
    renderCharts() {
      this.renderTrendChart()
      this.renderStatusChart()
      this.renderDeptChart()
      this.renderRoleChart()
    },

    // ① 近 7 天趋势：访问量(柱) + 新增用户/完成任务(双折线，右轴)
    renderTrendChart() {
      this.trendChart = echarts.init(this.$refs.trendRef)
      const days = this.weeklyTrend

      this.trendChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: {
          top: 0,
          icon: 'roundRect',
          itemWidth: 10,
          itemHeight: 10,
          textStyle: { color: '#606266' }
        },
        grid: { left: 8, right: 12, top: 48, bottom: 4, containLabel: true },
        xAxis: {
          type: 'category',
          data: days.map((d) => d.date),
          axisLine: { lineStyle: { color: '#DCDFE6' } },
          axisTick: { show: false },
          axisLabel: { color: '#909399' }
        },
        yAxis: [
          {
            type: 'value',
            name: '访问量',
            nameTextStyle: { color: '#909399', fontSize: 11 },
            splitLine: { lineStyle: { color: '#F0F2F5' } },
            axisLabel: { color: '#909399' }
          },
          {
            type: 'value',
            name: '任务 / 人',
            nameTextStyle: { color: '#909399', fontSize: 11 },
            splitLine: { show: false },
            axisLabel: { color: '#909399' }
          }
        ],
        series: [
          {
            name: '访问量',
            type: 'bar',
            barWidth: 18,
            data: days.map((d) => d.visits),
            itemStyle: {
              borderRadius: [6, 6, 0, 0],
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#8cc5ff' },
                { offset: 1, color: '#409EFF' }
              ])
            }
          },
          {
            name: '新增用户',
            type: 'line',
            smooth: true,
            yAxisIndex: 1,
            symbolSize: 7,
            data: days.map((d) => d.newUsers),
            itemStyle: { color: '#E6A23C' },
            lineStyle: { width: 2.5 }
          },
          {
            name: '完成任务',
            type: 'line',
            smooth: true,
            yAxisIndex: 1,
            symbolSize: 7,
            data: days.map((d) => d.completedTasks),
            itemStyle: { color: '#67C23A' },
            lineStyle: { width: 2.5 },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(103, 194, 58, 0.22)' },
                { offset: 1, color: 'rgba(103, 194, 58, 0)' }
              ])
            }
          }
        ]
      })
    },

    // ② 任务状态分布（环形图，中心文案由 HTML 叠加）
    renderStatusChart() {
      this.statusChart = echarts.init(this.$refs.statusRef)
      this.statusChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}：{c} 个（{d}%）' },
        legend: {
          bottom: 0,
          icon: 'circle',
          itemWidth: 8,
          itemHeight: 8,
          textStyle: { color: '#606266' }
        },
        series: [
          {
            name: '任务状态',
            type: 'pie',
            radius: ['58%', '78%'],
            center: ['50%', '45%'],
            avoidLabelOverlap: false,
            itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
            label: { show: false },
            labelLine: { show: false },
            data: [
              { value: this.overview.completedTasks, name: '已完成', itemStyle: { color: '#67C23A' } },
              { value: this.overview.inProgressTasks, name: '进行中', itemStyle: { color: '#409EFF' } },
              { value: this.overview.pendingTasks, name: '待处理', itemStyle: { color: '#E6A23C' } }
            ]
          }
        ]
      })
    },

    // ③ 部门任务进度（横向堆叠条）
    renderDeptChart() {
      this.deptChart = echarts.init(this.$refs.deptRef)
      const depts = this.departments

      this.deptChart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: {
          top: 0,
          icon: 'roundRect',
          itemWidth: 10,
          itemHeight: 10,
          textStyle: { color: '#606266' }
        },
        grid: { left: 8, right: 28, top: 44, bottom: 4, containLabel: true },
        xAxis: {
          type: 'value',
          splitLine: { lineStyle: { color: '#F0F2F5' } },
          axisLabel: { color: '#909399' }
        },
        yAxis: {
          type: 'category',
          inverse: true,
          data: depts.map((d) => d.name),
          axisLine: { lineStyle: { color: '#DCDFE6' } },
          axisTick: { show: false },
          axisLabel: { color: '#606266' }
        },
        series: [
          {
            name: '已完成',
            type: 'bar',
            stack: 'total',
            barWidth: 16,
            data: depts.map((d) => d.completed),
            itemStyle: { color: '#67C23A' }
          },
          {
            name: '进行中',
            type: 'bar',
            stack: 'total',
            barWidth: 16,
            data: depts.map((d) => d.inProgress),
            itemStyle: { color: '#409EFF', borderRadius: [0, 8, 8, 0] },
            label: {
              show: true,
              position: 'right',
              color: '#909399',
              fontSize: 11,
              formatter: (p) => {
                const d = depts[p.dataIndex]
                return d.completed + d.inProgress
              }
            }
          }
        ]
      })
    },

    // ④ 用户角色分布（饼图）
    renderRoleChart() {
      this.roleChart = echarts.init(this.$refs.roleRef)
      const palette = { 管理员: '#F56C6C', 部门经理: '#E6A23C', 普通成员: '#409EFF' }

      this.roleChart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}：{c} 人（{d}%）' },
        legend: {
          bottom: 0,
          icon: 'circle',
          itemWidth: 8,
          itemHeight: 8,
          textStyle: { color: '#606266' }
        },
        series: [
          {
            name: '角色分布',
            type: 'pie',
            radius: ['38%', '66%'],
            center: ['50%', '44%'],
            avoidLabelOverlap: true,
            itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 6 },
            label: {
              formatter: '{b}\n{d}%',
              color: '#606266',
              fontSize: 11,
              lineHeight: 16
            },
            labelLine: { length: 6, length2: 8 },
            data: this.userRoles.map((r) => ({
              value: r.value,
              name: r.name,
              itemStyle: { color: palette[r.name] || '#909399' }
            }))
          }
        ]
      })
    },

    handleResize() {
      this.trendChart && this.trendChart.resize()
      this.statusChart && this.statusChart.resize()
      this.deptChart && this.deptChart.resize()
      this.roleChart && this.roleChart.resize()
    },

    /* ---------------- 交互 ---------------- */
    handleRefresh() {
      this.refreshing = true
      // 这里可换成真实的统计数据接口，例如：
      // const res = await getOverview()
      setTimeout(() => {
        this.updatedAt = this.formatNow()
        this.refreshing = false
        ElMessage.success('数据已更新')
        // 数据若有变化，重绘图表
        this.renderCharts()
      }, 500)
    },

    formatNow() {
      const d = new Date()
      const p = (n) => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
    },

    /* ---------------- 表格展示 ---------------- */
    priorityType(p) {
      return { 高: 'danger', 中: 'warning', 低: 'info' }[p] || 'info'
    },
    statusType(s) {
      return { 已完成: 'success', 进行中: 'primary', 待处理: 'info' }[s] || 'info'
    },
    // 截止日期：已逾期 / 今天 / 即将到期
    dueState(row) {
      if (row.status === '已完成') {
        return { text: row.dueDate, type: 'done' }
      }
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      const due = new Date(row.dueDate + 'T00:00:00')
      const diff = Math.round((due - today) / 86400000)

      if (diff < 0) return { text: `${row.dueDate}（已逾期）`, type: 'overdue' }
      if (diff === 0) return { text: `${row.dueDate}（今天）`, type: 'today' }
      if (diff <= 2) return { text: `${row.dueDate}（${diff} 天后）`, type: 'soon' }
      return { text: row.dueDate, type: 'normal' }
    }
  }
}
</script>

<style scoped>
.dashboard {
  padding-bottom: 8px;
}

/* ============ 顶部欢迎栏 ============ */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 18px 22px;
  margin-bottom: 16px;
  border-radius: 10px;
  color: #fff;
  background: linear-gradient(120deg, #3a7bd5 0%, #4b8ef0 45%, #62a8ff 100%);
  box-shadow: 0 6px 18px rgba(64, 158, 255, 0.25);
}
.hero-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}
.hero-tag {
  transform: translateY(-1px);
}
.hero-sub {
  margin: 6px 0 0;
  font-size: 13px;
  opacity: 0.9;
}
.hero-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.update-time {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  opacity: 0.92;
}

/* ============ 指标卡 ============ */
.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
  margin-bottom: 16px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  transition: transform 0.2s, box-shadow 0.2s;
}
.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
}
.stat-icon {
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}
.stat-body {
  min-width: 0;
}
.stat-label {
  font-size: 13px;
  color: #909399;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.25;
  color: #303133;
}
.stat-unit {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.stat-foot {
  display: flex;
  gap: 12px;
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.foot-item b {
  font-weight: 600;
}

/* ============ 面板通用 ============ */
.mt16 {
  margin-top: 16px;
}
.panel {
  border-radius: 10px;
}
.panel :deep(.el-card__header) {
  padding: 14px 18px;
}
.panel :deep(.el-card__body) {
  padding: 16px 18px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.panel-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.panel-tip {
  font-size: 12px;
  color: #a8abb2;
}

/* ============ 图表 ============ */
.chart {
  width: 100%;
}
.chart-wrap {
  position: relative;
}
.donut-center {
  position: absolute;
  left: 50%;
  top: 45%;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}
.donut-value {
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.1;
}
.donut-label {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

/* ============ 动态时间线 ============ */
.act-timeline {
  padding-left: 4px;
}
.act-user {
  margin-right: 6px;
  font-weight: 600;
  color: #303133;
}
.act-text {
  font-size: 13px;
  color: #606266;
}

/* ============ 截止日期状态 ============ */
.due.normal {
  color: #606266;
}
.due.done {
  color: #a8abb2;
}
.due.soon {
  color: #e6a23c;
}
.due.today {
  color: #e6a23c;
  font-weight: 600;
}
.due.overdue {
  color: #f56c6c;
  font-weight: 600;
}

/* ============ 公告 ============ */
.notice {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-left: 3px solid #dcdfe6;
  border-radius: 8px;
  background: #fafbfc;
  transition: all 0.2s;
}
.notice:hover {
  border-color: #c6e2ff;
  border-left-color: #409eff;
  background: #f5faff;
}
.notice.is-important {
  border-left-color: #f56c6c;
  background: #fef6f6;
}
.notice-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.notice-date {
  font-size: 12px;
  color: #a8abb2;
}
.notice-title {
  margin-top: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

/* ============ 响应式 ============ */
@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }
  .stat-value {
    font-size: 22px;
  }
}
</style>
