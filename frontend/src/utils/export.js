// 前端 CSV 导出工具：把内存中的数据（如当前列表 / 报表结果）生成 CSV 并触发下载。
// 生成的文件带 UTF-8 BOM，Excel / WPS 可直接打开且中文不乱码。
// 若后续需要后端生成 .xlsx（POI / EasyExcel），把调用处替换为请求导出接口即可。

export function exportCsv(filename, rows, columns) {
  if (!rows || rows.length === 0) return false
  const header = columns.map((c) => c.label).join(',')
  const body = rows
    .map((row) => columns.map((c) => cell(typeof c.value === 'function' ? c.value(row) : row[c.key])).join(','))
    .join('\r\n')
  const csv = '\uFEFF' + header + '\r\n' + body
  download(new Blob([csv], { type: 'text/csv;charset=utf-8;' }), filename)
  return true
}

function cell(v) {
  if (v === null || v === undefined) return ''
  const s = String(v)
  return /[",\r\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s
}

function download(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
