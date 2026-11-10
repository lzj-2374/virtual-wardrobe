<template>
  <div class="page-container">
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">数据导出</h2>
        <p class="page-sub">把平台数据导出为 CSV，Excel / WPS 可直接打开</p>
      </div>
      <span class="header-code">UTF-8 BOM</span>
    </div>

    <div class="export-note">
      <el-icon><InfoFilled /></el-icon>
      <span>导出由后端 <code>/admin/export/{type}</code> 提供，文件为 CSV（UTF-8 BOM）。<b>只导账号管理字段与聚合统计，不含任何用户衣物内容。</b></span>
    </div>

    <div class="export-panel">
      <div
        v-for="(d, i) in datasets"
        :key="d.type"
        class="export-row"
      >
        <span class="export-idx">{{ String(i + 1).padStart(2, '0') }}</span>
        <span class="export-icon"><el-icon :size="20"><component :is="d.icon" /></el-icon></span>
        <span class="export-main">
          <span class="export-name">{{ d.label }}</span>
          <span class="export-desc">{{ d.desc }}</span>
        </span>
        <span class="export-fields">{{ d.columns.length }} 个字段</span>
        <button class="export-btn" :disabled="d._loading" @click="doExport(d)">
          <el-icon><Download /></el-icon>
          <span>{{ d._loading ? '导出中…' : '导出 CSV' }}</span>
        </button>
      </div>
    </div>

    <p class="export-foot">
      导出文件名格式：<code>{类型}_{日期}_{时分}.csv</code>，示例 <code>users_20261017_1240.csv</code>
    </p>
  </div>
</template>

<script setup>
import { reactive } from 'vue'
import { Download, UserFilled, Goods, Suitcase, DataAnalysis, InfoFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { fetchExportRows, getAdminOverview } from '@/api/admin'
import { exportCsv } from '@/utils/export'

const datasets = reactive([
  {
    type: 'users',
    label: '用户数据',
    desc: '账号管理必需字段，不含手机号、邮箱、头像与密码',
    icon: UserFilled,
    columns: [
      { key: 'id', label: 'ID' },
      { key: 'username', label: '用户名' },
      { key: 'nickname', label: '昵称' },
      { key: 'role', label: '角色', value: (r) => (r.role === 1 ? '管理员' : '普通用户') },
      { key: 'status', label: '状态', value: (r) => (r.status === 1 ? '启用' : '停用') },
      { key: 'createTime', label: '注册时间' }
    ],
    _loading: false
  },
  {
    type: 'category-stats',
    label: '品类分布',
    desc: '各品类的衣物件数与占比，聚合统计',
    icon: Goods,
    columns: [
      { key: 'name', label: '品类' },
      { key: 'value', label: '件数' },
      { key: 'ratio', label: '占比', value: (r) => `${r.ratio ?? 0}%` }
    ],
    _loading: false
  },
  {
    type: 'scene-stats',
    label: '场景分布',
    desc: '各场景的搭配套数与占比，聚合统计',
    icon: Suitcase,
    columns: [
      { key: 'name', label: '场景' },
      { key: 'value', label: '套数' },
      { key: 'ratio', label: '占比', value: (r) => `${r.ratio ?? 0}%` }
    ],
    _loading: false
  },
  {
    type: 'overview',
    label: '报表汇总',
    desc: '平台核心指标汇总（用户 / 衣物 / 搭配）',
    icon: DataAnalysis,
    custom: true,
    columns: [
      { key: 'metric', label: '指标' },
      { key: 'value', label: '数值' }
    ],
    _loading: false
  }
])

function stamp() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}_${p(d.getHours())}${p(d.getMinutes())}`
}

async function doExport(d) {
  d._loading = true
  try {
    let rows
    if (d.custom) {
      const ov = await getAdminOverview()
      rows = [
        { metric: '用户总数', value: ov.totalUser ?? 0 },
        { metric: '衣物总数', value: ov.totalClothing ?? 0 },
        { metric: '搭配总数', value: ov.totalOutfit ?? 0 },
        { metric: '今日新增用户', value: ov.todayNewUser ?? 0 }
      ]
    } else {
      rows = await fetchExportRows(d.type)
    }
    const ok = exportCsv(`${d.type}_${stamp()}.csv`, rows, d.columns)
    if (ok) ElMessage.success(`「${d.label}」导出成功`)
    else ElMessage.warning('当前没有可导出的数据')
  } catch (e) {
    // 拦截器已提示
  } finally {
    d._loading = false
  }
}
</script>

<style scoped>
.header-left { display: flex; flex-direction: column; gap: 6px; }
.page-sub {
  margin: 0;
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
  letter-spacing: .04em;
}
.header-code {
  font-family: var(--vw-serif);
  font-size: 11px;
  letter-spacing: .18em;
  color: var(--vw-gold);
  padding: 4px 10px;
  border: 1px solid var(--vw-line);
  border-radius: 2px;
  background: var(--vw-surface-2);
}

/* 顶部说明 */
.export-note {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  padding: 11px 16px;
  font-size: 12.5px;
  color: var(--vw-ink-mute);
  background: var(--vw-surface-2);
  border-left: 2px solid var(--vw-gold);
  border-radius: 2px;
}
.export-note .el-icon { color: var(--vw-gold); }
.export-note code,
.export-foot code {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  color: var(--vw-ink-2);
  background: var(--vw-surface-3);
  padding: 1px 5px;
  border-radius: 2px;
}

/* 列表容器 */
.export-panel {
  background: var(--vw-surface);
  border: 1px solid var(--vw-line);
  border-radius: 3px;
  overflow: hidden;
}
.export-row {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 24px;
  border-bottom: 1px solid #f0ece2;
  transition: background .2s;
}
.export-row:last-child { border-bottom: none; }
.export-row:hover { background: #fbf8f0; }

.export-idx {
  font-family: var(--vw-serif);
  font-size: 15px;
  color: var(--vw-ink-mute);
  letter-spacing: .1em;
  width: 26px;
  flex-shrink: 0;
}
.export-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--vw-gold);
  background: var(--vw-surface-2);
  border: 1px solid var(--vw-line);
  border-radius: 2px;
}
.export-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.export-name {
  font-family: var(--vw-serif);
  font-size: 15.5px;
  font-weight: 600;
  letter-spacing: .1em;
  color: var(--vw-ink);
}
.export-desc {
  font-size: 12.5px;
  color: var(--el-text-color-secondary);
  letter-spacing: .02em;
}
.export-fields {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--vw-ink-mute);
  letter-spacing: .06em;
  padding: 0 18px;
  border-right: 1px solid #efeae0;
}

.export-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  font-size: 13px;
  letter-spacing: .08em;
  color: var(--vw-ink);
  background: #fff;
  border: 1px solid var(--vw-ink-3);
  border-radius: 2px;
  cursor: pointer;
  transition: all .25s ease;
}
.export-btn:hover:not(:disabled) {
  color: #f7f3e8;
  background: var(--vw-ink);
  border-color: var(--vw-ink);
}
.export-btn:disabled { opacity: .55; cursor: default; }

.export-foot {
  margin: 14px 2px 0;
  font-size: 12px;
  color: var(--vw-ink-mute);
  letter-spacing: .03em;
}
</style>
