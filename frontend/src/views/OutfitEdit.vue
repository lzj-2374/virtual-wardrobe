<template>
  <div class="page-container" v-loading="loading">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ isEdit ? '编辑搭配' : '新建搭配' }}</h2>
        <p class="page-sub">挑选单品 · 上传封面 · 保存到我的搭配</p>
      </div>
      <div class="header-actions">
        <el-button v-if="isEdit" type="danger" plain :loading="deleting" @click="onDelete">删除搭配</el-button>
        <el-button @click="$router.push('/workspace/outfits')">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="pickedCount === 0" @click="onSave">保存搭配</el-button>
      </div>
    </div>

    <div class="editor">
      <!-- 左：单品槽位 + 搭配信息 -->
      <div class="editor-main">
        <section class="pane">
          <div class="pane-head">
            <span class="pane-idx">01</span>
            <span class="pane-title">挑选单品</span>
            <span class="pane-hint">点击槽位从衣柜中选择 · 已选 {{ pickedCount }} 件</span>
          </div>

          <div class="slot-grid">
            <button
              v-for="role in roles"
              :key="role.value"
              type="button"
              class="slot"
              :class="{ 'is-filled': !!selected[role.value] }"
              @click="openPicker(role)"
            >
              <template v-if="selected[role.value]">
                <el-image :src="clothingOf(selected[role.value]).imageUrl" fit="contain" class="slot-img" />
                <span class="slot-clear" title="移除" @click.stop="clearRole(role.value)">×</span>
              </template>
              <div v-else class="slot-empty">
                <span class="slot-plus">+</span>
                <span class="slot-tip">选择{{ role.label }}</span>
              </div>
              <span class="slot-foot">
                <span class="slot-role">{{ role.label }}</span>
                <span class="slot-name">{{ selected[role.value] ? clothingOf(selected[role.value]).name : '未选择' }}</span>
              </span>
            </button>
          </div>
        </section>

        <section class="pane">
          <div class="pane-head">
            <span class="pane-idx">02</span>
            <span class="pane-title">搭配信息</span>
          </div>
          <el-form :model="form" label-width="64px" class="info-form">
            <el-form-item label="名称">
              <el-input v-model="form.name" placeholder="如：清爽通勤风" maxlength="20" show-word-limit />
            </el-form-item>
            <div class="form-grid">
              <el-form-item label="场景">
                <el-select v-model="form.scene" style="width: 100%">
                  <el-option v-for="s in scenes" :key="s" :label="s" :value="s" />
                </el-select>
              </el-form-item>
              <el-form-item label="季节">
                <el-select v-model="form.season" style="width: 100%">
                  <el-option v-for="s in seasons" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="说明">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                maxlength="120"
                show-word-limit
                placeholder="一句话描述这套搭配，如：风衣配白 T，干净利落"
              />
            </el-form-item>
          </el-form>
        </section>
      </div>

      <!-- 右：封面 + 保存 -->
      <aside class="editor-side">
        <section class="pane">
          <div class="pane-head">
            <span class="pane-idx">03</span>
            <span class="pane-title">搭配封面</span>
          </div>

          <el-upload
            class="cover-uploader"
            drag
            :auto-upload="false"
            :show-file-list="false"
            :on-change="onCoverChange"
            accept="image/*"
          >
            <div class="cover-box" :class="{ 'has-img': !!form.coverUrl }">
              <!-- contain 而不是 cover：这是"预览最终封面长什么样"，用 cover 会把图裁掉一部分，
                   用户以为存进去就是这样，实际列表里又是另一个样子 -->
              <el-image v-if="form.coverUrl" :src="form.coverUrl" fit="contain" class="cover-img" />
              <div v-else class="cover-ph">
                <el-icon :size="26"><Picture /></el-icon>
                <p>上传搭配封面</p>
                <small>建议 3:4 竖版，单张 ≤ 5MB</small>
              </div>
              <div v-if="coverUploading" class="cover-mask">
                <el-icon class="is-loading" :size="22"><Loading /></el-icon>
              </div>
            </div>
          </el-upload>

          <div class="cover-actions">
            <el-button v-if="form.coverUrl" size="small" text @click="form.coverUrl = ''">移除封面</el-button>
            <span v-else class="cover-note">不传也可以，列表会显示占位图</span>
          </div>
        </section>

        <el-button class="save-btn" type="primary" size="large" :loading="saving" :disabled="pickedCount === 0" @click="onSave">
          保存搭配
        </el-button>
      </aside>
    </div>

    <!-- 单品选择弹窗 -->
    <el-dialog v-model="picker.visible" :title="`选择${picker.role?.label || ''}`" width="720px">
      <div v-if="pickerList.length" class="picker-grid">
        <div
          v-for="c in pickerList"
          :key="c.id"
          class="pick-card"
          :class="{ 'is-active': selected[picker.role.value] === c.id }"
          @click="choose(c)"
        >
          <el-image :src="c.imageUrl" fit="contain" class="pick-img" />
          <span class="pick-name">{{ c.name }}</span>
        </div>
      </div>
      <el-empty v-else :description="`衣柜里还没有${picker.role?.label || '这类'}，先去添加吧`" :image-size="80" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture, Loading } from '@element-plus/icons-vue'
import { pageClothing } from '@/api/clothing'
import { getOutfit, createOutfit, updateOutfit, deleteOutfit } from '@/api/outfit'
import { uploadFile } from '@/api/file'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const roles = [
  { label: '上装', value: 1, cat: 1 },
  { label: '下装', value: 2, cat: 2 },
  { label: '外套', value: 3, cat: 3 },
  { label: '鞋', value: 4, cat: 5 },
  { label: '配饰', value: 5, cat: 5 }
]
const scenes = ['日常', '通勤', '约会', '运动', '正式']
const seasons = [{ label: '春夏', value: 3 }, { label: '秋冬', value: 12 }, { label: '四季', value: 15 }]

const allClothing = ref([])
const selected = reactive({ 1: '', 2: '', 3: '', 4: '', 5: '' })
const form = reactive({ name: '', scene: '日常', season: 3, description: '', coverUrl: '' })
const loading = ref(false)
const saving = ref(false)
const deleting = ref(false)
const coverUploading = ref(false)
const picker = reactive({ visible: false, role: null })

const clothingByCat = (cat) => allClothing.value.filter((c) => c.category === cat)
const clothingOf = (id) => allClothing.value.find((c) => c.id === id) || {}
const pickedCount = computed(() => roles.filter((r) => selected[r.value]).length)
const pickerList = computed(() => (picker.role ? clothingByCat(picker.role.cat) : []))

function openPicker(role) {
  picker.role = role
  picker.visible = true
}
function choose(c) {
  selected[picker.role.value] = c.id
  picker.visible = false
}
function clearRole(value) {
  selected[value] = ''
}

async function onCoverChange(upload) {
  const raw = upload?.raw
  if (!raw) return
  if (raw.size > 5 * 1024 * 1024) {
    ElMessage.warning('封面请小于 5MB')
    return
  }
  coverUploading.value = true
  try {
    const res = await uploadFile(raw)
    form.coverUrl = res.url || res.thumbUrl || ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    coverUploading.value = false
  }
}

async function loadClothing() {
  const res = await pageClothing({ page: 1, size: 200 })
  allClothing.value = res.records
}

async function loadOutfit() {
  if (!isEdit.value) return
  const res = await getOutfit(route.params.id)
  form.name = res.name || ''
  form.scene = res.scene || '日常'
  form.season = res.season || 3
  form.description = res.description || ''
  form.coverUrl = res.coverUrl || ''
  ;(res.items || []).forEach((it) => { selected[it.role] = it.clothingId })
}

async function onSave() {
  const items = roles
    .filter((r) => selected[r.value])
    .map((r) => ({ role: r.value, clothingId: selected[r.value] }))
  if (items.length === 0) {
    ElMessage.warning('请至少选择一件单品')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('给这套搭配起个名字吧')
    return
  }
  saving.value = true
  try {
    const payload = { ...form, items }
    if (isEdit.value) {
      await updateOutfit(route.params.id, payload)
    } else {
      payload.source = 1
      await createOutfit(payload)
    }
    ElMessage.success('保存成功')
    router.push('/workspace/outfits')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  try {
    await ElMessageBox.confirm(`确认删除搭配「${form.name || '未命名搭配'}」吗？`, '删除搭配', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch (e) {
    return // 用户取消
  }
  deleting.value = true
  try {
    await deleteOutfit(route.params.id)
    ElMessage.success('已删除')
    router.push('/workspace/outfits')
  } catch (e) {
    // 拦截器已提示
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    await loadClothing()
    await loadOutfit()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.page-sub { margin: 4px 0 0; font-size: 12px; color: var(--vw-ink-mute); }
.header-actions { display: flex; gap: 10px; }

/* 整体两栏：左内容自适应，右固定 300px */
.editor { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; }
.editor-main { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.editor-side { position: sticky; top: 18px; display: flex; flex-direction: column; gap: 14px; }

.pane {
  background: var(--el-card-bg-color, var(--vw-surface));
  border: 1px solid var(--vw-line);
  border-radius: 3px;
  padding: 18px 20px 20px;
}
.pane-head { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.pane-idx {
  font-family: var(--vw-serif);
  font-size: 13px;
  color: var(--vw-gold);
  border: 1px solid rgba(162, 129, 63, .35);
  border-radius: 2px;
  padding: 1px 6px;
  letter-spacing: .06em;
}
.pane-title { font-size: 15px; font-weight: 600; color: var(--vw-ink); letter-spacing: .04em; }
.pane-hint { margin-left: auto; font-size: 12px; color: var(--vw-ink-mute); }

/* 槽位 */
.slot-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; }
.slot {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: 0;
  border: 1px dashed #d6d0c2;
  border-radius: 3px;
  background: #fbf9f3;
  cursor: pointer;
  overflow: hidden;
  font: inherit;
  transition: border-color .18s, box-shadow .18s, transform .18s;
}
.slot:hover { border-color: var(--vw-gold); box-shadow: 0 6px 18px rgba(23, 25, 29, .08); transform: translateY(-1px); }
.slot.is-filled { border-style: solid; border-color: var(--vw-line); background: var(--vw-surface); }
.slot-img { width: 100%; aspect-ratio: 3 / 4; display: block; background: var(--vw-img-bg); }
.slot-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  aspect-ratio: 3 / 4;
  color: #a9a396;
}
.slot-plus {
  font-size: 22px;
  line-height: 1;
  font-weight: 300;
  width: 34px; height: 34px;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid currentColor;
  border-radius: 50%;
}
.slot-tip { font-size: 12px; }
.slot-clear {
  position: absolute;
  top: 6px; right: 6px;
  width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%;
  background: rgba(23, 25, 29, .62);
  color: #fff;
  font-size: 13px;
  line-height: 1;
  opacity: 0;
  transition: opacity .18s;
}
.slot:hover .slot-clear { opacity: 1; }
.slot-foot {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 8px 8px;
  text-align: left;
  border-top: 1px solid var(--vw-line);
  background: var(--vw-surface);
}
.slot-role { font-size: 12px; color: var(--vw-gold); letter-spacing: .06em; }
.slot-name {
  font-size: 12px;
  color: var(--vw-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.slot:not(.is-filled) .slot-name { color: #b3ada0; }

/* 表单 */
.info-form :deep(.el-form-item) { margin-bottom: 16px; }
.info-form :deep(.el-form-item:last-child) { margin-bottom: 0; }
/* 场景 + 季节两列等宽。名称单独占一整行（见模板）。
   关键那行是 label 的 flex: 0 0 64px —— el-form-item__label 默认 flex-shrink: 1，
   会被 flex 压缩，两个格子里的控件内容不同 → 压出来的 label 宽度不同 →
   剩下的下拉框宽度就不一样了。锁死 label 宽之后，内容区才真正等宽。 */
.form-grid { display: flex; gap: 0 18px; }
.form-grid :deep(.el-form-item) { flex: 1 1 0; min-width: 0; }
.form-grid :deep(.el-form-item__label) { flex: 0 0 64px; padding-right: 12px; }
.form-grid :deep(.el-form-item__content) { flex: 1 1 0; min-width: 0; }
.form-grid :deep(.el-select) { width: 100% !important; }
/* 季节格比场景格低了一点，往上提 2px 对齐。
   transform 只影响绘制位置、不影响布局，改大改小都不会挤到别的东西 */
.form-grid :deep(.el-form-item:last-child) { transform: translateY(-8px); }
.form-grid :deep(.el-form-item:last-child .el-form-item__label) { transform: translateY(6px); }

/* 封面 */
.cover-uploader :deep(.el-upload) { width: 100%; }
.cover-uploader :deep(.el-upload-dragger) { padding: 0; border: none; background: transparent; }
.cover-box {
  position: relative;
  width: 100%;
  aspect-ratio: 3 / 4;
  border: 1px dashed #d6d0c2;
  border-radius: 3px;
  overflow: hidden;
  background: #fbf9f3;
  transition: border-color .18s;
}
.cover-uploader:hover .cover-box { border-color: var(--vw-gold); }
.cover-box.has-img { border-style: solid; border-color: var(--vw-line); }
.cover-img { width: 100%; height: 100%; display: block; }
.cover-ph {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #a9a396;
}
.cover-ph p { margin: 0; font-size: 13px; color: var(--vw-ink-3); }
.cover-ph small { font-size: 11px; color: #b3ada0; }
.cover-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(250, 248, 242, .78);
  color: var(--vw-ink-2);
}
.cover-actions { margin-top: 10px; min-height: 24px; display: flex; align-items: center; }
.cover-note { font-size: 12px; color: var(--vw-ink-mute); }

.save-btn { width: 100%; letter-spacing: .12em; }

/* 选择弹窗 */
.picker-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; max-height: 52vh; overflow-y: auto; }
.pick-card {
  border: 1px solid var(--vw-line);
  border-radius: 3px;
  overflow: hidden;
  cursor: pointer;
  background: var(--vw-surface);
  transition: border-color .18s, box-shadow .18s;
}
.pick-card:hover { border-color: var(--vw-gold-2); box-shadow: 0 6px 16px rgba(23, 25, 29, .08); }
.pick-card.is-active { border-color: var(--vw-gold); box-shadow: inset 0 0 0 1px var(--vw-gold); }
.pick-img { width: 100%; aspect-ratio: 3 / 4; display: block; background: var(--vw-img-bg); }
.pick-name {
  display: block;
  padding: 7px 8px;
  font-size: 12px;
  color: var(--vw-ink-2);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 1080px) {
  .editor { grid-template-columns: minmax(0, 1fr); }
  .editor-side { position: static; }
  .slot-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .form-grid { flex-direction: column; gap: 0; }
}
</style>
