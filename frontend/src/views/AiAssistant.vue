<template>
  <div class="page-container ai-page">
    <div class="page-header ai-header">
      <h2 class="page-title">AI 穿搭助手</h2>
      <div class="ai-header-actions">
        <el-tag type="info">基于你的真实衣柜推荐</el-tag>
        <el-button size="small" :icon="Picture" @click="openHistory">历史效果图</el-button>
        <el-button size="small" :icon="RefreshLeft" :disabled="!messages.length" @click="newChat">新对话</el-button>
      </div>
    </div>

    <!-- 空衣柜引导：推荐强依赖衣柜内容，没衣服先提示去录 -->
    <div v-if="wardrobeCount === 0" class="empty-guide">
      <div class="eg-text">
        <strong>衣柜还是空的</strong>
        <span class="text-muted">AI 只会在你已有的衣物里搭配，先录入几件，推荐才准。</span>
      </div>
      <el-button type="primary" :icon="Plus" @click="$router.push('/workspace/wardrobe/add')">去工作台添加衣物</el-button>
    </div>

    <div class="chat-box" ref="chatRef">
      <ChatBubble v-for="(m, i) in messages" :key="i" :role="m.role">
        <div>
          <p class="msg-text">{{ m.content }}</p>
          <div v-if="m.recommendations && m.recommendations.length" class="rec-list">
            <el-card v-for="rec in m.recommendations" :key="rec.id" class="rec-card" shadow="hover">
              <div class="rec-head">
                <strong>{{ rec.name }}</strong>
                <!-- 文案三态：已出图 → 重新生成；手上还攥着 taskId（上次等超时了）→ 接着查，不重复花钱；
                     什么都没提交过 → 正常生成 -->
                <el-button size="small" :type="rec.imageStatus === 2 ? 'success' : 'primary'" :loading="rec._generating" @click="genImage(rec)">
                  {{ rec.imageStatus === 2 ? '重新生成' : (rec._taskId ? '再查一次' : '生成效果图') }}
                </el-button>
              </div>
              <p class="rec-reason">{{ rec.reason }}</p>
              <p class="rec-color text-muted">配色：{{ rec.colorNote }}</p>
              <div class="rec-items">
                <div v-for="it in rec.items" :key="it.clothingId" class="rec-item">
                  <el-image v-if="clothMap[it.clothingId]?.imageUrl" :src="clothMap[it.clothingId].imageUrl" fit="contain" class="rec-thumb" />
                  <div v-else class="rec-thumb ph">{{ roleName(it.role) }}</div>
                  <span class="rec-item-name">{{ clothMap[it.clothingId]?.name || '单品' }}</span>
                </div>
              </div>
              <div class="rec-image">
                <el-skeleton v-if="rec.imageStatus === 1" animated style="width:360px;max-width:100%;margin:0 auto">
                  <template #template><el-skeleton-item variant="image" style="width:100%;height:480px" /></template>
                </el-skeleton>
                <el-image
                  v-else-if="rec.imageStatus === 2 && rec.imageUrl"
                  :src="effectSrc(rec)"
                  fit="contain"
                  class="rec-effect"
                  @error="onEffectError(rec)"
                  @click="openEffect(rec.imageUrl)"
                >
                  <!-- 加载失败时不要只留一个破碎图标：把话说清楚，
                       既能点开新窗口看原图，也方便排查到底是哪一步丢了 URL -->
                  <template #error>
                    <div class="rec-eff-err">效果图加载失败<br />点击用新窗口打开</div>
                  </template>
                </el-image>
                <div v-else-if="rec.imageStatus === 2" class="rec-effect ph">上身效果图（示意）</div>
              </div>

              <div class="rec-actions">
                <!-- 按钮在右下、说明文字在左：操作是主动作，应该落在视觉终点；
                     原来按钮在左下、提示在右下，视线从左往右读完提示才发现按钮，反了 -->
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :loading="rec._saving"
                  :disabled="!!rec.saved"
                  @click="saveAsOutfit(rec)"
                >
                  {{ rec.saved ? '已放入我的搭配' : '放入我的搭配' }}
                </el-button>
                <el-button v-if="rec.saved" size="small" text @click="$router.push('/outfits')">去查看</el-button>
                <span v-if="rec.imageStatus === 2 && rec.imageUrl" class="rec-tip">保存时会把效果图作为封面</span>
              </div>
            </el-card>
          </div>
        </div>
      </ChatBubble>
      <div v-if="sending" class="typing text-muted">AI 正在思考…</div>
    </div>

    <!-- 本轮指定的单品。放在输入框正上方而不是藏进输入框里：
         这几个 id 会一直跟着后面每一句话发出去，必须让人一眼看见自己"还钉着"什么东西 -->
    <div v-if="pinned.length" class="pinned-bar">
      <span class="pinned-label">本轮指定</span>
      <el-tag
        v-for="p in pinned"
        :key="p.id"
        size="small"
        closable
        effect="plain"
        @close="removePinned(p.id)"
      >
        {{ p.name }}
      </el-tag>
      <el-button link size="small" @click="pinned = []">清空</el-button>
    </div>

    <div class="input-bar">
      <!-- 「+」：从衣柜里挑几件，强制用进这一轮搭配 -->
      <el-button class="plus-btn" :icon="Plus" :disabled="!wardrobeList.length" @click="openPicker" />
      <el-input
        v-model="input"
        type="textarea"
        :rows="2"
        resize="none"
        placeholder="描述你的需求，如：明天面试穿什么？ / 今天 15 度想穿舒服点"
        @keyup.ctrl.enter="send"
      />
      <el-button type="primary" :icon="Promotion" :loading="sending" @click="send">发送</el-button>
    </div>

    <!-- 衣柜选择器：只从用户自己的衣柜里挑，挑中的会作为硬约束发给后端 -->
    <el-dialog v-model="pickerVisible" title="从衣柜里选单品" width="640px" append-to-body>
      <p class="picker-tip text-muted">
        最多选 3 件。选中的单品会被强制用进这一轮搭配，其他位置仍由 AI 自由发挥。
      </p>
      <div v-if="wardrobeList.length" class="picker-grid">
        <div
          v-for="c in wardrobeList"
          :key="c.id"
          class="pick-item"
          :class="{ on: pickerTemp.includes(c.id) }"
          @click="togglePick(c.id)"
        >
          <el-image v-if="c.imageUrl" :src="c.imageUrl" fit="contain" class="pick-img" />
          <div v-else class="pick-img pick-empty">无图</div>
          <div class="pick-name" :title="c.name">{{ c.name }}</div>
          <div class="pick-sub text-muted">{{ clothSub(c) }}</div>
        </div>
      </div>
      <el-empty v-else description="衣柜还是空的，先去添加衣物" :image-size="70" />
      <template #footer>
        <el-button @click="pickerVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!pickerTemp.length" @click="confirmPick">
          确定{{ pickerTemp.length ? `（${pickerTemp.length}/3）` : '' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 历史效果图。后端只返回「真的出过图」的推荐，所以不会出现空白格；
         点图直接在新窗口打开原图，比在抽屉里缩放好用 -->
    <el-drawer v-model="historyVisible" title="历史效果图" size="480px">
      <div v-loading="historyLoading" class="hist-wrap">
        <p v-if="!historyLoading && !historyList.length" class="text-muted hist-empty">
          还没有生成过效果图。<br />在上面的推荐卡片上点「生成效果图」，出图后就会自动收进这里。
        </p>
        <div class="hist-grid">
          <div v-for="h in historyList" :key="h.id" class="hist-item">
            <el-image
              :src="h.imageUrl"
              fit="cover"
              class="hist-img"
              @click="openEffect(h.imageUrl)"
            />
            <div class="hist-name">{{ h.name || '搭配' }}</div>
            <div class="hist-meta text-muted">{{ h.scene || '日常' }} · {{ seasonLabel(h.season) }}</div>
            <el-button link type="primary" size="small" class="hist-detail-btn" @click="openHistoryDetail(h)">
              查看详情
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 历史效果图的详情抽屉。结构刻意和「我的搭配」里那个只读抽屉保持一致：
         同一套搭配从哪个入口点进来，看到的东西应该长一个样 -->
    <el-drawer v-model="detailVisible" :title="detailItem.name || '搭配详情'" size="440px" direction="rtl">
      <div v-if="detailItem.id" class="detail">
        <div class="detail-cover">
          <el-image
            :src="detailItem.imageUrl"
            fit="contain"
            class="detail-cover-img"
            @click="openEffect(detailItem.imageUrl)"
          />
        </div>

        <div class="detail-meta">
          <el-tag size="small" type="success">{{ detailItem.scene || '未设场景' }}</el-tag>
          <el-tag size="small" effect="plain">{{ seasonLabel(detailItem.season) }}</el-tag>
          <el-tag size="small" effect="plain">AI 生成</el-tag>
          <span class="text-muted">{{ detailItem.items?.length || 0 }} 件单品</span>
        </div>

        <p v-if="detailItem.reason" class="detail-desc text-muted">{{ detailItem.reason }}</p>
        <p v-if="detailItem.colorNote" class="detail-color text-muted">配色：{{ detailItem.colorNote }}</p>

        <div class="detail-sub-title">包含单品</div>
        <div v-if="detailItem.items && detailItem.items.length" class="detail-items">
          <div v-for="it in detailItem.items" :key="it.clothingId" class="d-item">
            <el-image
              v-if="clothMap[it.clothingId]?.imageUrl"
              :src="clothMap[it.clothingId].imageUrl"
              fit="contain"
              class="d-item-img"
            />
            <div v-else class="d-item-img d-item-empty">无图</div>
            <div class="d-item-text">
              <div class="d-item-name">{{ clothMap[it.clothingId]?.name || roleName(it.role) }}</div>
              <div class="text-muted d-item-sub">{{ itemSub(it) }}</div>
            </div>
          </div>
        </div>
        <p v-else class="text-muted detail-desc">这套搭配的单品记录已经找不到了。</p>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { Promotion, Plus, Picture, RefreshLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import ChatBubble from '@/components/ChatBubble.vue'
import { aiChat, generateImage, getImageStatus, getImageHistory, saveRecommendationAsOutfit } from '@/api/ai'
import { pageClothing } from '@/api/clothing'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/**
 * 会话要能扛住「切页面」和「刷新」。
 * <p>
 * Vue Router 默认不做 keep-alive，一离开这个页面组件就被销毁了，
 * 原来 messages 只放在组件内存里，所以点去别的页面再回来必然是一片空白。
 * 这里把「最后一条会话 id + 消息快照」存进 localStorage，回来直接接着聊。
 * key 里带 userId 是为了同一台电脑换账号登录时不串数据。
 */
const sessionKey = () => `ai:session:${userStore.userInfo?.id || 'anon'}`
const bootSession = readSession()

const messages = reactive([])
const input = ref('')
const sending = ref(false)
const chatRef = ref()
const clothMap = reactive({})
const wardrobeCount = ref(null)
const conversationId = ref(bootSession?.conversationId || null)
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])

// 衣柜数组。clothMap 是按 id 反查用的（推荐卡片渲染单品缩略图），
// 选择器要按顺序铺开，所以另存一份数组
const wardrobeList = ref([])
// 本轮指定的单品，存完整对象——直接就能拿到名字和图片，不用再查一次
const pinned = ref([])
const pickerVisible = ref(false)
const pickerTemp = ref([])
const roleNames = { 1: '上装', 2: '下装', 3: '外套', 4: '鞋', 5: '配饰' }
const roleName = (r) => roleNames[r] || '单品'

async function send() {
  const text = input.value.trim()
  if (!text || sending.value) return
  messages.push({ role: 'user', content: text })
  input.value = ''
  sending.value = true
  scroll()
  try {
    // 带上上一次的会话 id，后端才知道这是同一个会话的第几句。
    // 以前这里写死 99，而 99 不属于任何用户，后端校验不过就每次新建会话——
    // 结果就是"每问一句都是新对话"，上下文也就无从谈起
    const res = await aiChat({
      message: text,
      conversationId: conversationId.value,
      // 指定的单品会一直跟着后面每一句话发出去，直到用户点掉那个标签。
      // 这样"用这件外套搭一套" → "那正式点的呢" 能沿用同一件
      clothingIds: pinned.value.map((c) => c.id)
    })
    if (res.conversationId) conversationId.value = res.conversationId
    messages.push({ role: 'ai', content: '为你推荐以下搭配：', recommendations: res.recommendations })
    saveSession()
  } catch (e) {
    messages.push({ role: 'ai', content: '抱歉，推荐出了点问题，请稍后再试。' })
  } finally {
    sending.value = false
    scroll()
  }
}

// 万相实测一次要 60 秒上下（还要排队），所以轮询的时间上限必须给够。
// 之前这里写的是"最多轮询 30 次"→30 秒一到就 clearInterval，
// 结果就是：图早就出好了、库里 image_status 也是 2，页面却永远停在骨架屏上，
// 用户只能跑去 OSS 控制台自己下载——"生成成功了但页面显示不出"就是这么来的。
const POLL_INTERVAL_MS = 1500
// 后端的预算：等万相出图最多 120s，再加上下载 + 传 OSS，整条链路最坏 150s 上下。
// 前端必须比它长，否则后端还在跑，这边先报超时，等于白问。给到 240s 留足余量。
const POLL_MAX_MS = 240000

async function genImage(rec) {
  if (rec._generating) return
  // 上次是"等到超时"而不是"真的失败"：taskId 还在手里，续查就行。
  // 重新提交等于再排一次队、再花一次钱，出来的还是同一张图。
  if (rec._taskId) {
    pollImage(rec, rec._taskId)
    return
  }
  rec._generating = true
  rec.imageStatus = 1
  try {
    const res = await generateImage({ recommendationId: rec.id })
    rec._taskId = res.taskId
    pollImage(rec, res.taskId)
  } catch (e) {
    rec.imageStatus = 0
    rec._generating = false
  }
}

// 轮询生图状态（后端由 RabbitMQ 消费者异步出图）
function pollImage(rec, taskId) {
  rec._generating = true
  const startedAt = Date.now()
  const timer = setInterval(async () => {
    if (Date.now() - startedAt > POLL_MAX_MS) {
      clearInterval(timer)
      rec._generating = false
      // 故意不动 imageStatus、也不清 taskId：后端多半还在跑，
      // 按钮会变成「再查一次」，点一下接着等，不用重新生成
      ElMessage.warning('生图比预期慢，4 分钟还没出结果。可以点「再查一次」继续等。')
      saveSession()
      return
    }
    try {
      const st = await getImageStatus(taskId)
      if (st.status === 2) {
        clearInterval(timer)
        rec.imageStatus = 2
        rec.imageUrl = st.imageUrl
        rec._taskId = null
        rec._generating = false
        // 立刻存一次：不然用户刚出图就切走页面，回来又得重新生成
        saveSession()
      } else if (st.status === 3) {
        clearInterval(timer)
        rec.imageStatus = 0
        rec._taskId = null
        rec._generating = false
        ElMessage.error('生图失败，可重试')
        saveSession()
      }
      // 还是 1（生成中）：什么也不做，下一轮接着问
    } catch (e) {
      // 单次请求失败大概率是网络抖了一下，不判死刑，交给下一轮；
      // 真正的兜底是上面的时间上限，不会无限轮询
    }
  }, POLL_INTERVAL_MS)
}

// 显示用的 src。正常情况下就是后端给的地址；失败重试过一次时带上 _r 参数绕缓存。
// 单独用一个字段而不是改 imageUrl，是为了让 imageUrl 保持干净——
// 它后面还要当 outfit 的封面地址存库，不该带上一串缓存参数。
function effectSrc(rec) {
  return rec._imgSrc || rec.imageUrl
}

// 效果图加载失败时，把真实的 src 打到控制台。
// 这是唯一能区分"地址本身就是错的"和"地址对但请求被拦了"的线索，
// 光看页面上的破碎图标什么也判断不了。
function onEffectError(rec) {
  console.warn('[AI 效果图] 加载失败，src =', rec.imageUrl)
  // el-image 失败后不会自己再试，一次网络抖动就会让它永久破图。
  // 这里补一次重试：加查询参数是为了绕开缓存里那条失败记录，OSS 会忽略未知参数，不影响取图。
  if (!rec._imgRetried && rec.imageUrl) {
    rec._imgRetried = true
    const url = rec.imageUrl
    setTimeout(() => {
      rec._imgSrc = url + (url.includes('?') ? '&' : '?') + '_r=' + Date.now()
    }, 1200)
  }
}

function openEffect(url) {
  if (url) window.open(url, '_blank')
}

// ===================== 会话持久化 =====================

function readSession() {
  try {
    const raw = localStorage.getItem(sessionKey())
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    // 存的内容格式坏了就当没有，不能让一条脏数据把整个页面搞白
    return null
  }
}

/**
 * 把当前对话存到本地。
 * <p>
 * 两个细节：
 * <ul>
 *   <li>只留最近 20 条消息。聊得再长也没必要全存，localStorage 有配额，写爆了会连 token 一起存不进去</li>
 *   <li>序列化时丢掉下划线开头的字段。{@code _generating}、{@code _taskId} 是运行时状态，
 *       存下来没意义，恢复出来还会让按钮一直转圈</li>
 * </ul>
 */
function saveSession() {
  try {
    localStorage.setItem(sessionKey(), JSON.stringify(
      { conversationId: conversationId.value, messages: messages.slice(-20) },
      (key, value) => (key.startsWith('_') ? undefined : value)
    ))
  } catch (e) {
    // 配额满了就放弃持久化，不影响当前这轮对话
  }
}

function restoreSession() {
  if (!bootSession || !Array.isArray(bootSession.messages) || !bootSession.messages.length) {
    return false
  }
  bootSession.messages.forEach((m) => {
    // 快照时可能正好在生成图（imageStatus=1），但刷新之后已经没有轮询在跑了。
    // 不改回 0 的话，这张卡片会永远停在骨架屏，点按钮也只会变成"重新生成"
    if (Array.isArray(m.recommendations)) {
      m.recommendations.forEach((r) => {
        if (r.imageStatus === 1) r.imageStatus = 0
      })
    }
    messages.push(m)
  })
  return true
}

// 开一段新对话：清空屏幕、清空本地、把会话 id 交还给后端去新建
function newChat() {
  if (!messages.length) return
  messages.splice(0, messages.length)
  conversationId.value = null
  try {
    localStorage.removeItem(sessionKey())
  } catch (e) {
    // 忽略
  }
  messages.push({ role: 'ai', content: '你好！告诉我场合、天气或你已有的单品（点左下角 + 可指定），我来帮你搭配 👗' })
}

// ===================== 指定单品（输入框的「+」） =====================

function clothSub(c) {
  return [c.subCategory, c.color].filter(Boolean).join(' · ')
}

function openPicker() {
  // 打开时把已选的带进去，用户想加一件不用重新选一遍
  pickerTemp.value = pinned.value.map((c) => c.id)
  pickerVisible.value = true
}

function togglePick(id) {
  const i = pickerTemp.value.indexOf(id)
  if (i >= 0) {
    pickerTemp.value.splice(i, 1)
    return
  }
  if (pickerTemp.value.length >= 3) {
    ElMessage.warning('最多选 3 件')
    return
  }
  pickerTemp.value.push(id)
}

function confirmPick() {
  // 按衣柜顺序取，保证标签顺序稳定，不随点击先后乱跳
  pinned.value = wardrobeList.value.filter((c) => pickerTemp.value.includes(c.id))
  pickerVisible.value = false
}

function removePinned(id) {
  pinned.value = pinned.value.filter((c) => c.id !== id)
}

// ===================== 历史效果图 =====================

async function openHistory() {
  historyVisible.value = true
  historyLoading.value = true
  try {
    historyList.value = await getImageHistory()
  } catch (e) {
    // 拦截器已经弹过提示了
  } finally {
    historyLoading.value = false
  }
}

// 位掩码转人话：5 → 春秋。历史图列表里用它说明这套适合什么季节
function seasonLabel(mask) {
  if (!mask) return '四季'
  const names = []
  if (mask & 1) names.push('春')
  if (mask & 2) names.push('夏')
  if (mask & 4) names.push('秋')
  if (mask & 8) names.push('冬')
  return names.length ? names.join('') : '四季'
}

// ===================== 历史效果图详情 =====================

const detailVisible = ref(false)
const detailItem = ref({})

function openHistoryDetail(h) {
  detailItem.value = h
  detailVisible.value = true
}

// 单品下面那行小字：优先「子品类 · 颜色」，衣柜里查不到就退回位置名（上装/鞋…）
function itemSub(it) {
  const c = clothMap[it.clothingId]
  if (!c) return roleName(it.role)
  return clothSub(c) || roleName(it.role)
}

function scroll() {
  nextTick(() => {
    if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight
  })
}

// 把推荐结果沉淀为一条正式搭配（source=2），效果图顺带作为封面
async function saveAsOutfit(rec) {
  if (rec.saved || rec._saving) return
  rec._saving = true
  try {
    await saveRecommendationAsOutfit(rec.id, {
      name: rec.name || 'AI 推荐搭配',
      scene: rec.scene || '日常',
      season: rec.season || 15,
      description: rec.reason || '',
      coverUrl: rec.imageUrl || ''
    })
    rec.saved = true
    ElMessage.success('已放入我的搭配')
  } catch (e) {
    // 拦截器已提示
  } finally {
    rec._saving = false
  }
}

onMounted(async () => {
  // 先把上次的对话接上，再去拉衣柜——卡片里的单品缩略图要靠衣柜数据还原，
  // 但两者没有先后依赖，恢复对话更快，所以放前面，界面不闪
  const restored = restoreSession()
  try {
    const res = await pageClothing({ page: 1, size: 200 })
    res.records.forEach((c) => { clothMap[c.id] = c })
    wardrobeList.value = res.records
    wardrobeCount.value = res.total ?? res.records.length
  } catch (e) {
    wardrobeCount.value = null
  }
  // 没有历史才放欢迎语。否则每次从别的页面回来都会多一句"你好！"，越攒越多
  if (!restored) {
    messages.push({ role: 'ai', content: '你好！告诉我场合、天气或你已有的单品（点左下角 + 可指定），我来帮你搭配 👗' })
  }
  scroll()
})
</script>

<style scoped>
.ai-page { display: flex; flex-direction: column; height: calc(100vh - 120px); }
/* 标题和右边那排按钮分开站，窗口窄的时候自动换行，不至于把标题挤没 */
.ai-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.ai-header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
/* 历史效果图抽屉 */
.hist-wrap { min-height: 200px; }
.hist-empty { font-size: 13px; line-height: 1.9; }
.hist-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.hist-item { min-width: 0; }
/* 抽屉里两列，宽度不固定，所以用 aspect-ratio 让高度跟着宽度走。
   写成固定 180px 高的话，3:4 的竖图又会被裁掉头，和卡片上原来的毛病一样 */
.hist-img { width: 100%; aspect-ratio: 3 / 4; border-radius: 2px; background: var(--vw-img-bg); cursor: zoom-in; }
.hist-name { font-size: 12px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.hist-meta { font-size: 11px; }
.hist-detail-btn { margin-top: 2px; padding: 0; height: auto; font-size: 12px; }

/* ---------- 历史图详情抽屉：和「我的搭配」那套只读抽屉同一份样式 ---------- */
.detail { display: flex; flex-direction: column; gap: 14px; }
.detail-cover {
  width: 100%; aspect-ratio: 3 / 4; max-height: 520px;
  border-radius: 3px; overflow: hidden; background: var(--vw-img-bg); cursor: zoom-in;
}
.detail-cover-img { width: 100%; height: 100%; }
.detail-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; flex-wrap: wrap; }
.detail-desc { font-size: 13px; line-height: 1.7; margin: 0; }
.detail-color { font-size: 12px; margin: 0; }
.detail-sub-title {
  font-family: var(--vw-serif); font-weight: 600; letter-spacing: .12em;
  color: var(--vw-ink); padding-top: 6px; border-top: 1px solid var(--vw-line);
}
.detail-items { display: flex; flex-direction: column; gap: 10px; }
.d-item { display: flex; align-items: center; gap: 12px; }
.d-item-img { width: 52px; height: 52px; border-radius: 2px; background: var(--vw-img-bg); flex: none; }
.d-item-empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--vw-ink-mute); }
.d-item-name { font-size: 13px; font-weight: 500; }
.d-item-sub { font-size: 12px; }
.empty-guide {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 12px 16px; margin-bottom: 12px;
  background: #fbf7ec; border: 1px solid #eadfc4; border-left: 2px solid var(--vw-gold);
  border-radius: 2px;
}
.eg-text { display: flex; flex-direction: column; gap: 3px; font-size: 13px; }
.eg-text strong { font-family: var(--vw-serif); letter-spacing: .06em; color: var(--vw-ink); }
.eg-text .text-muted { font-size: 12px; }
.chat-box { flex: 1; overflow-y: auto; padding-right: 8px; }
.typing { margin: 8px 0 8px 40px; font-size: 13px; }
.msg-text { margin: 0 0 8px; }
.rec-list { display: flex; flex-direction: column; gap: 12px; }
.rec-card { border-radius: 3px; }
.rec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.rec-reason { font-size: 13px; margin: 4px 0; }
.rec-color { font-size: 12px; margin: 0 0 8px; }
.rec-items { display: flex; gap: 10px; flex-wrap: wrap; margin-bottom: 10px; }
.rec-item { width: 72px; text-align: center; }
.rec-thumb { width: 72px; height: 90px; border-radius: 2px; background: var(--vw-img-bg); }
.rec-thumb.ph { display: flex; align-items: center; justify-content: center; color: var(--vw-ink-mute); font-size: 12px; }
.rec-item-name { display: block; font-size: 11px; color: var(--vw-ink-mute); margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.rec-image { margin-top: 4px; }
/* 效果图是 3:4 的竖图（实测 1104×1472）。原来给的是 width:100% + height:160px，
   一个扁框配 fit="cover"，等于把头和腿都裁掉——"显示不全"就是这么来的。
   现在按竖版比例定框 + contain，整张图完整可见，两侧留白也看不出来（图本身就是浅灰底）。
   宽高取 330×440 是为了正好贴合 3:4，不留多余边。 */
.rec-effect { width: 360px; height: 480px; max-width: 100%; margin: 0 auto; display: block; border-radius: 2px; }
.rec-effect.ph { display: flex; align-items: center; justify-content: center; background: var(--vw-surface-2); color: var(--vw-ink-mute); text-align: center; padding: 0 12px; box-sizing: border-box; }
/* 破图时的兜底文案。铺满 el-image 给的那块区域就行，不要再套一层 .rec-effect，
   否则等于在 330×440 的盒子里再放一个 330×440，撑出来很难看 */
.rec-eff-err { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; text-align: center; padding: 0 12px; box-sizing: border-box; font-size: 12px; line-height: 1.8; color: var(--vw-ink-mute); background: var(--vw-surface-2); }
.rec-actions { display: flex; align-items: center; gap: 8px; margin-top: 10px; padding-top: 10px; border-top: 1px dashed var(--vw-line); }
/* order:-1 把提示文字提到最左，按钮就自然被推到右下角。
   比调换 DOM 顺序好在：无障碍读屏和 Tab 焦点顺序仍按"按钮在前"走，只改视觉位置 */
.rec-tip { order: -1; margin-right: auto; font-size: 11px; color: var(--vw-ink-mute); }
.input-bar { display: flex; gap: 10px; align-items: flex-end; margin-top: 12px; }
.input-bar .el-button { height: 54px; }
/* 「+」和发送按钮同高但更窄，视觉上从属于输入框，而不是另一个主操作 */
.input-bar .plus-btn { width: 46px; flex: none; }

/* —— 本轮指定单品 —— */
.pinned-bar { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 10px; }
.pinned-bar + .input-bar { margin-top: 8px; }
.pinned-label { font-size: 11px; color: var(--vw-ink-mute); letter-spacing: .08em; }

/* —— 衣柜选择器 —— */
.picker-tip { font-size: 12px; margin: 0 0 12px; }
.picker-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); gap: 10px;
  max-height: 52vh; overflow-y: auto; padding-right: 4px;
}
.pick-item {
  border: 1px solid var(--vw-line); border-radius: 3px; padding: 6px; cursor: pointer;
  transition: border-color .15s, background .15s, box-shadow .15s;
}
.pick-item:hover { border-color: var(--vw-gold); }
/* 选中态靠描边 + 底色，不靠对勾：缩略图上盖个勾会把衣服挡住 */
.pick-item.on { border-color: var(--vw-gold); background: rgba(162, 129, 63, .10); box-shadow: inset 0 0 0 1px var(--vw-gold); }
.pick-img { width: 100%; aspect-ratio: 3 / 4; border-radius: 2px; background: var(--vw-img-bg); display: block; }
.pick-empty { display: flex; align-items: center; justify-content: center; font-size: 11px; color: var(--vw-ink-mute); }
.pick-name { font-size: 12px; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pick-sub { font-size: 11px; }
</style>
