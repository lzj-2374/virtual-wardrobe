<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">数据报表</h2>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :span="6" v-for="s in cards" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon">
              <el-icon :size="22"><component :is="s.icon" /></el-icon>
            </div>
            <div>
              <div class="stat-num">{{ s.value }}</div>
              <div class="text-muted">{{ s.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt">
      <template #header><span class="card-title">用户增长趋势</span></template>
      <div ref="trendRef" class="chart-box"></div>
    </el-card>

    <el-row :gutter="16" class="mt">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span class="card-title">衣物品类分布</span></template>
          <div ref="categoryRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span class="card-title">搭配来源分布</span></template>
          <div ref="sourceRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, onBeforeUnmount, watch } from 'vue'
import { Refresh, UserFilled, Goods, Suitcase, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getAdminOverview } from '@/api/admin'
import { useTheme } from '@/composables/useTheme'

const { theme } = useTheme()
const chartTheme = computed(() => theme.value === 'dark'
  ? { accent: '#c9ad72', border: '#1c1f24' }
  : { accent: '#a2813f', border: '#fff' })

const loading = ref(false)
const data = reactive({
  totalUser: 0,
  totalClothing: 0,
  totalOutfit: 0,
  todayNewUser: 0,
  userTrend: null,
  categoryDistribution: [],
  sourceDistribution: []
})
const trendRef = ref()
const categoryRef = ref()
const sourceRef = ref()
let charts = []

const cards = computed(() => [
  { label: '用户总数', value: data.totalUser, icon: UserFilled },
  { label: '衣物总数', value: data.totalClothing, icon: Goods },
  { label: '搭配总数', value: data.totalOutfit, icon: Suitcase },
  { label: '今日新增用户', value: data.todayNewUser, icon: TrendCharts }
])

function dispose() {
  charts.forEach((c) => c.dispose())
  charts = []
}

function renderCharts() {
  dispose()
  if (trendRef.value && data.userTrend?.dates?.length) {
    const c = echarts.init(trendRef.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 10, right: 20, top: 24, bottom: 10, containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: data.userTrend.dates },
      yAxis: { type: 'value', minInterval: 1 },
      series: [{ type: 'line', smooth: true, areaStyle: { opacity: 0.15 }, itemStyle: { color: chartTheme.value.accent }, lineStyle: { width: 2 }, data: data.userTrend.values }]
    })
    charts.push(c)
  }
  if (categoryRef.value && data.categoryDistribution?.length) {
    const c = echarts.init(categoryRef.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie', radius: ['42%', '70%'], itemStyle: { borderRadius: 6, borderColor: chartTheme.value.border, borderWidth: 2 },
        label: { show: false }, data: data.categoryDistribution
      }]
    })
    charts.push(c)
  }
  if (sourceRef.value && data.sourceDistribution?.length) {
    const c = echarts.init(sourceRef.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie', radius: ['0%', '65%'], itemStyle: { borderColor: chartTheme.value.border, borderWidth: 2 },
        data: data.sourceDistribution
      }]
    })
    charts.push(c)
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getAdminOverview()
    Object.assign(data, res)
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
    await nextTick()
    renderCharts()
  }
}

onMounted(load)

// 切主题后按新配色重画，ECharts 不认 CSS 变量
watch(theme, async () => {
  await nextTick()
  renderCharts()
})
onBeforeUnmount(dispose)
</script>

<style scoped>
.stat-card { border-radius: 3px; }
.stat-inner { display: flex; align-items: center; gap: 14px; padding: 4px 2px; }
.stat-icon { width: 46px; height: 46px; border-radius: 2px; display: flex; align-items: center; justify-content: center; color: var(--vw-gold); background: var(--vw-surface-2); border: 1px solid var(--vw-line); }
.stat-num { font-family: var(--vw-serif); font-size: 28px; font-weight: 600; color: var(--vw-ink); line-height: 1.2; }
.mt { margin-top: 16px; }
.card-title { font-family: var(--vw-serif); font-weight: 600; letter-spacing: .1em; color: var(--vw-ink); }
.chart-box { height: 280px; }
</style>
