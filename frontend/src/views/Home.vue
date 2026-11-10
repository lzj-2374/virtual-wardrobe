<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">数据统计</h2>
      <el-button :icon="View" @click="$router.push('/ai')">浏览态预览</el-button>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col :span="6" v-for="s in stats" :key="s.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-inner">
            <div class="stat-icon">
              <el-icon :size="22"><component :is="s.icon" /></el-icon>
            </div>
            <div class="stat-text">
              <div class="stat-num">{{ s.value }}</div>
              <div class="text-muted">{{ s.label }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :span="10">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">品类分布</span>
              <span class="card-hint">ECharts · GET /statistics/overview</span>
            </div>
          </template>
          <div ref="pieRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-head">
              <span class="card-title">风格分布</span>
              <span class="card-hint">ECharts · GET /statistics/overview</span>
            </div>
          </template>
          <div ref="barRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="mt recent-card">
      <template #header>
        <div class="card-head">
          <span class="card-title">最近添加</span>
          <el-button link type="primary" @click="$router.push('/workspace/wardrobe')">查看全部</el-button>
        </div>
      </template>
      <div class="grid-cards">
        <ClothingCard v-for="c in overview.recent" :key="c.id" :clothing="c" @click="goClothing" />
        <el-empty v-if="!overview.recent || overview.recent.length === 0" description="暂无衣物" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, View, Goods, Suitcase, Box, Collection } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import ClothingCard from '@/components/ClothingCard.vue'
import { getOverview } from '@/api/statistics'
import { useTheme } from '@/composables/useTheme'

const { theme } = useTheme()

const router = useRouter()
const overview = reactive({ recent: [] })
const pieRef = ref()
const barRef = ref()
let charts = []

function goClothing(item) {
  router.push(`/workspace/wardrobe/${item.id ?? item}`)
}

const stats = computed(() => [
  { label: '衣物总数', value: overview.totalClothing || 0, icon: Goods },
  { label: '搭配数', value: overview.totalOutfit || 0, icon: Suitcase },
  { label: '品类数', value: overview.categoryCount || 0, icon: Box },
  { label: '风格数', value: overview.styleCount || 0, icon: Collection }
])

// ECharts 不认 CSS 变量，配色必须按主题手动给。
// 切主题时 dispose 旧实例重画，否则深底上还是浅色坐标轴，几乎看不清。
const chartTheme = computed(() => (theme.value === 'dark'
  ? {
      ink: ['#e8e3d6', '#c9c2b0', '#a6a49c', '#8b8b88', '#c9ad72', '#e0c68c'],
      label: '#9a9c9f',
      border: '#1c1f24',
      axis: 'rgba(255,255,255,.14)',
      split: 'rgba(255,255,255,.08)',
      bar: '#c9ad72',
      accent: '#e0c68c'
    }
  : {
      ink: ['#24272c', '#4a4e55', '#7c818a', '#b3b6b2', '#cfc7b4', '#a2813f'],
      label: '#6f747c',
      border: '#fbf9f4',
      axis: '#e4dfd3',
      split: '#eeeae0',
      bar: '#3a3e45',
      accent: '#a2813f'
    }))

function renderCharts() {
  if (pieRef.value && overview.byCategory) {
    const pie = echarts.init(pieRef.value)
    pie.setOption({
      color: chartTheme.value.ink,
      tooltip: { trigger: 'item', formatter: '{b}: {c} 件 ({d}%)' },
      legend: { bottom: 0, type: 'scroll', itemWidth: 8, itemHeight: 8, textStyle: { color: chartTheme.value.label } },
      series: [{
        type: 'pie',
        radius: ['44%', '70%'],
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 2, borderColor: chartTheme.value.border, borderWidth: 2 },
        label: { show: false },
        data: overview.byCategory.filter((c) => c.value > 0).map((c) => ({ name: c.name, value: c.value }))
      }]
    })
    charts.push(pie)
  }
  if (barRef.value && overview.byStyle) {
    const bar = echarts.init(barRef.value)
    bar.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 10, right: 20, top: 20, bottom: 10, containLabel: true },
      xAxis: {
        type: 'category',
        data: overview.byStyle.map((s) => s.name),
        axisLabel: { interval: 0, color: chartTheme.value.label },
        axisLine: { lineStyle: { color: chartTheme.value.axis } },
        axisTick: { show: false }
      },
      yAxis: {
        type: 'value',
        minInterval: 1,
        axisLabel: { color: chartTheme.value.label },
        splitLine: { lineStyle: { color: chartTheme.value.split } }
      },
      series: [{
        type: 'bar',
        barWidth: '46%',
        itemStyle: { color: chartTheme.value.bar, borderRadius: [2, 2, 0, 0] },
        emphasis: { itemStyle: { color: chartTheme.value.accent } },
        data: overview.byStyle.map((s) => s.value)
      }]
    })
    charts.push(bar)
  }
}

onMounted(async () => {
  try {
    const res = await getOverview()
    Object.assign(overview, res)
  } catch (e) {}
  await nextTick()
  renderCharts()
})

// 切主题后按新配色重画（renderCharts 内部会先 dispose 旧实例）
watch(theme, async () => {
  await nextTick()
  renderCharts()
})

onBeforeUnmount(() => {
  charts.forEach((c) => c.dispose())
})
</script>

<style scoped>
.stat-card { border-radius: 3px; }
.stat-inner { display: flex; align-items: center; gap: 14px; padding: 4px 2px; }
.stat-icon {
  width: 46px; height: 46px; border-radius: 2px;
  display: flex; align-items: center; justify-content: center;
  color: var(--vw-gold);
  background: var(--vw-surface-2);
  border: 1px solid var(--vw-line);
}
.stat-num {
  font-family: var(--vw-serif);
  font-size: 28px; font-weight: 600; color: var(--vw-ink); line-height: 1.2;
}
.stat-text .text-muted { font-size: 12px; letter-spacing: .08em; }
.mt { margin-top: 16px; }
.chart-card { border-radius: 3px; }
.card-head { display: flex; align-items: center; justify-content: space-between; }
.card-title {
  font-family: var(--vw-serif);
  font-weight: 600; letter-spacing: .12em; color: var(--vw-ink);
}
.card-hint { font-size: 12px; color: var(--vw-ink-mute); letter-spacing: .04em; }
.chart-box { height: 260px; }
.grid-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; }
</style>
