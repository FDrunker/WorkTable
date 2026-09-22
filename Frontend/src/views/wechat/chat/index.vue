<template>
  <div class="wechat-app">
    <!-- ============ 左栏：会话列表（按昵称分类） ============ -->
    <aside class="side-panel">
      <div class="side-search">
        <el-input v-model="keyword" placeholder="搜索昵称" clearable size="default">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="side-body" v-loading="sessionLoading">
        <div
          v-for="item in filteredSessions"
          :key="item.chatUsername"
          class="session-item"
          :class="{ active: activeUsername === item.chatUsername }"
          @click="selectSession(item)"
        >
          <div class="avatar" :class="{ group: item.group }">
            <el-icon v-if="item.group"><UserFilled /></el-icon>
            <span v-else>{{ shortName(item.displayName) }}</span>
          </div>
          <div class="session-main">
            <div class="session-top">
              <span class="session-name">{{ item.displayName }}</span>
              <span class="session-time">{{ shortTime(item.lastTime) }}</span>
              <el-button
                v-if="item.sessionId"
                link
                size="small"
                class="session-delete"
                title="删除会话"
                @click.stop="removeSession(item)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="session-bottom">
              <span class="session-preview">{{ item.lastMessage || '暂无归档消息' }}</span>
            </div>
          </div>
        </div>

        <el-empty v-if="!sessionLoading && filteredSessions.length === 0" description="没有会话" :image-size="80" />
      </div>

      <div class="side-foot">
        <el-tooltip :content="importHint" placement="top">
          <span class="import-wrap">
            <el-button
              type="primary"
              size="small"
              class="import-btn"
              :disabled="!importEnabled"
              @click="openImportDialog"
            >
              <el-icon><DocumentAdd /></el-icon>
              <span>导入文本</span>
            </el-button>
          </span>
        </el-tooltip>
        <div class="side-status">
          <el-tooltip :content="statusText" placement="top">
            <el-tag :type="statusReady ? 'success' : 'warning'" size="small" effect="plain">
              {{ statusReady ? '数据源就绪' : '数据源未就绪' }}
            </el-tag>
          </el-tooltip>
          <el-button link size="small" @click="loadStatus">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>
    </aside>

    <!-- ============ 中栏：聊天记录（微信风格气泡） ============ -->
    <main class="chat-panel">
      <template v-if="activeSession">
        <header class="chat-header">
          <div class="chat-title">
            <span class="chat-name">{{ activeSession.displayName }}</span>
            <el-tag v-if="activeSession.group" size="small" type="info" effect="plain">群聊</el-tag>
            <el-tag v-if="!activeSession.sessionId" size="small" type="warning" effect="plain">未归档</el-tag>
          </div>

          <div class="chat-actions">
            <el-tag v-if="dirtyCount > 0" type="danger" size="small" effect="dark">
              新增 {{ dirtyCount }} 条待同步
            </el-tag>
            <el-button size="small" :loading="syncing" @click="syncMessages">
              <el-icon><Refresh /></el-icon>
              <span>同步记录</span>
            </el-button>
          </div>
        </header>

        <div v-if="activeSession.sessionId" class="relationship-bar">
          <div class="relationship-item affinity-item">
            <div class="relationship-copy">
              <span class="relationship-label">当前好感度</span>
              <span class="relationship-value">
                {{ formatScore(activeSession.affinityScore) }}
                <small v-if="activeSession.affinityScore != null">%</small>
              </span>
            </div>
            <div class="affinity-track" aria-hidden="true">
              <span :style="{ width: affinityWidth(activeSession.affinityScore) }"></span>
            </div>
          </div>

          <div class="relationship-item acquaintance-item">
            <span class="relationship-label">认识</span>
            <el-button
              link
              size="small"
              class="acquaintance-link"
              :loading="acquaintanceSaving"
              @click="openAcquaintanceDialog"
            >
              {{ activeSession.acquaintanceDays != null ? `认识 ${activeSession.acquaintanceDays} 天` : '选择认识日期' }}
            </el-button>
          </div>

          <div class="relationship-item performance-item">
            <span class="relationship-label">我的发挥</span>
            <span class="performance-score">{{ formatScore(activeSession.myPerformanceScore) }}</span>
          </div>

          <el-button type="primary" size="small" class="next-step-btn" @click="nextStepDialog = true">
            建议
          </el-button>
        </div>

        <div ref="messageListRef" class="message-list" v-loading="messageLoading">
          <template v-for="(msg, index) in messages" :key="msg.messageId">
            <div v-if="showDateDivider(index)" class="date-divider">
              <span>{{ msg.date }}</span>
            </div>

            <div
              class="msg-row"
              :class="{
                self: msg.senderRole === 'SELF',
                unanalyzed: !msg.analyzed
              }"
            >
              <div v-if="msg.senderRole !== 'SELF'" class="msg-avatar">
                {{ shortName(senderLabel(msg)) }}
              </div>

              <div class="msg-body">
                <div v-if="activeSession.group && msg.senderRole !== 'SELF'" class="msg-sender">
                  {{ senderLabel(msg) }}
                </div>
                <!-- 用 div 而非 span 承载内容，避免 {{ }} 表达式跨行触发 prettier 误格式化 -->
                <div class="msg-bubble" :class="bubbleClass(msg)">{{ msg.content }}</div>
                <div v-if="msg.senderRole === 'TARGET'" class="msg-insights">
                  <div class="insight-line">
                    <span class="insight-label">情绪</span>
                    <el-tag size="small" effect="plain" type="warning">
                      {{ msg.emotionTag || '待分析' }}
                    </el-tag>
                    <span class="insight-percent">{{ formatPercent(msg.emotionPercent) }}</span>
                  </div>
                  <div class="insight-line">
                    <span class="insight-label">意图</span>
                    <el-tag size="small" effect="plain" type="info">
                      {{ msg.intentTag || '待分析' }}
                    </el-tag>
                    <span class="insight-percent">{{ formatPercent(msg.intentPercent) }}</span>
                  </div>
                </div>
                <div v-else-if="msg.senderRole === 'SELF'" class="reply-rating">
                  <span class="insight-label">回答评级</span>
                  <el-tag v-if="msg.replyRating" size="small" type="success" effect="plain">
                    {{ msg.replyRating }}
                  </el-tag>
                  <span v-else class="rating-pending">待评估</span>
                  <span v-if="msg.replyScore != null" class="reply-score">{{ formatScore(msg.replyScore) }} 分</span>
                </div>
                <div class="msg-meta">
                  <span class="msg-time">{{ onlyTime(msg.time) }}</span>
                </div>
              </div>

              <div v-if="msg.senderRole === 'SELF'" class="msg-avatar self-avatar">
                {{ shortName(selfLabel) }}
              </div>
            </div>
          </template>

          <el-empty v-if="!messageLoading && messages.length === 0" description="暂无聊天记录" />
        </div>
      </template>

      <el-empty v-else description="请从左侧选择一个会话" />
    </main>

    <!-- ============ 建议弹窗 ============ -->
    <el-dialog v-model="nextStepDialog" title="建议" width="560px">
      <div v-if="activeSession?.nextStepSuggestion" class="next-step-content">
        {{ activeSession.nextStepSuggestion }}
      </div>
      <el-empty v-else description="暂无建议，待后续分析生成" :image-size="80" />
      <template #footer>
        <el-button type="primary" @click="nextStepDialog = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- ============ 导入文本对话框（数据来源：粘贴文本） ============ -->
    <el-dialog v-model="importDialog" title="导入聊天记录文本" width="660px" top="5vh">
      <el-alert type="info" :closable="false" show-icon class="dlg-alert"
        title="每 3 行一条消息：第 1 行昵称、第 2 行时间、第 3 行消息内容。">
        <div class="import-format">
          时间支持 2026-04-01 19:02、2026/4/1 19:02、4月1日 19:02（省略年份按当年计算）。<br />
          [语音] 与 [动画表情] 会照常显示在聊天记录里，但不参与分析。
        </div>
      </el-alert>

      <el-input
        v-model="importForm.text"
        type="textarea"
        :rows="16"
        resize="none"
        class="import-textarea"
        placeholder="在此粘贴聊天记录，例如：&#10;小美&#10;2026-04-01 19:02&#10;在吗？&#10;&#10;我&#10;2026-04-01 19:03&#10;在的"
      />

      <template #footer>
        <el-button @click="importDialog = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="confirmImport">确认并解析</el-button>
      </template>
    </el-dialog>

    <!-- ============ 选择自己的昵称 ============ -->
    <el-dialog v-model="identityDialog" title="选择聊天记录中的自己" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="选择后，其他昵称会成为会话名称，本次记录会追加到对应会话。"
        class="dlg-alert"
      />
      <el-form label-width="80px">
        <el-form-item label="我的昵称">
          <el-select v-model="identityForm.selfLabel" placeholder="请选择" class="full-width">
            <el-option
              v-for="s in pendingImport?.senders || []"
              :key="s.label"
              :label="s.label + '（' + s.count + '条）'"
              :value="s.label"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="targetLabelPreview" label="会话名称">
          <span class="conversation-preview">{{ targetLabelPreview }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelIdentity">取消</el-button>
        <el-button type="primary" :loading="identitySaving" @click="saveIdentity">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- ============ 认识日期 ============ -->
    <el-dialog v-model="acquaintanceDialog" title="选择认识日期" width="420px">
      <el-date-picker
        v-model="acquaintanceDraft"
        type="date"
        value-format="YYYY-MM-DD"
        format="YYYY-MM-DD"
        placeholder="选择认识日期"
        clearable
        :disabled-date="disableFutureDate"
        class="acquaintance-dialog-picker"
      />
      <template #footer>
        <el-button @click="acquaintanceDialog = false">取消</el-button>
        <el-button type="primary" :loading="acquaintanceSaving" @click="saveAcquaintanceDate">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, DocumentAdd, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import {
  commitWechatText,
  deleteWechatSession,
  getWechatStatus,
  listWechatSessions,
  openWechatChat,
  previewWechatText,
  syncWechatChat,
  updateWechatAcquaintanceDate
} from '@/api/wechat/chat'
import type {
  WechatChatDetail,
  WechatChatMessage,
  WechatSessionView,
  WechatTextImportPreview
} from '@/types'

/** 左上角搜索关键字 */
const keyword = ref('')
const sessionLoading = ref(false)
const messageLoading = ref(false)
const syncing = ref(false)
const identitySaving = ref(false)
const identityDialog = ref(false)
const nextStepDialog = ref(false)
const acquaintanceDialog = ref(false)

/** 导入文本对话框 */
const importDialog = ref(false)
const importing = ref(false)
const pendingImport = ref<WechatTextImportPreview | null>(null)

/** 认识日期编辑状态 */
const acquaintanceDraft = ref('')
const acquaintanceSaving = ref(false)

const sessions = ref<WechatSessionView[]>([])
const activeSession = ref<WechatSessionView | null>(null)
const messages = ref<WechatChatMessage[]>([])

const statusReady = ref(false)
const statusText = ref('正在检测数据源…')
/** 当前数据源标识：只有 text 支持导入文本 */
const statusMode = ref('')

const importForm = reactive<{ text: string }>({
  text: ''
})

/** 当前数据源是否支持文本导入（未知时按支持处理，避免状态接口失败时按钮直接消失） */
const importEnabled = computed(() => !statusMode.value || statusMode.value === 'text')

const importHint = computed(() =>
  importEnabled.value
    ? '粘贴聊天记录，解析后归档展示'
    : `当前数据源「${statusMode.value}」不支持文本导入，请把 wechat.datasource 设为 text`
)

const messageListRef = ref<HTMLElement | null>(null)

const identityForm = reactive<{ selfLabel: string }>({
  selfLabel: ''
})

/**
 * 当前会话在数据源里新增、但尚未归档的条数。
 *
 * 拿到会话详情时用 total 与已渲染条数对比得出，用于提示用户"有新消息待同步"。
 */
const dirtyCount = ref(0)

const activeUsername = computed(() => activeSession.value?.chatUsername ?? '')

const filteredSessions = computed<WechatSessionView[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return sessions.value
  }
  return sessions.value.filter((s: WechatSessionView) => (s.displayName || '').toLowerCase().includes(kw))
})

/** 自己那一侧的昵称，用于气泡头像 */
const selfLabel = computed(() => {
  const labels = activeSession.value?.selfLabels ?? []
  return labels.length ? labels[0] : '我'
})

const targetLabelPreview = computed(() =>
  (pendingImport.value?.senders || [])
    .map((sender) => sender.label)
    .filter((label) => label !== identityForm.selfLabel)
    .join('、')
)

onMounted(async () => {
  await loadStatus()
  await loadSessions()
})

/** 数据源状态自检 */
async function loadStatus() {
  try {
    const res = await getWechatStatus()
    statusReady.value = !!res.data?.ready
    statusText.value = res.data?.message || ''
    statusMode.value = res.data?.mode || ''
  } catch {
    statusReady.value = false
    statusText.value = '无法获取数据源状态'
    statusMode.value = ''
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await listWechatSessions()
    sessions.value = res.data || []
  } catch {
    /*
      接口不可用时（后端没起来、或跑的是不含 /wechat/* 的旧编译产物）只清空列表。
      具体原因已由请求拦截器弹出提示；这里必须吞掉异常 ——
      它是 onMounted 里 await 的，抛出去会变成
      "Unhandled error during execution of mounted hook"，整页直接报错，
      用户反而看不到"数据源未就绪 + 没有会话"这两个更准确的界面信号。
    */
    sessions.value = []
  } finally {
    sessionLoading.value = false
  }
}

/**
 * 选择会话 —— 只读取聊天记录，不触发任何分析。
 */
async function selectSession(item: WechatSessionView) {
  activeSession.value = item
  acquaintanceDraft.value = item.acquaintanceDate || ''
  messages.value = []
  dirtyCount.value = 0

  await loadMessages(item)
}

/**
 * 读取聊天记录。
 *
 * sync=true 时后端会做一次幂等同步，但不会调用 LLM。
 */
async function loadMessages(item: WechatSessionView) {
  messageLoading.value = true
  try {
    const res = await openWechatChat({
      sessionId: item.sessionId,
      chatUsername: item.chatUsername,
      displayName: item.displayName,
      group: item.group,
      offset: 0,
      limit: 200,
      sync: true
    })
    const detail = res.data
    if (!detail) {
      return
    }
    await applyDetail(detail)
  } catch (e: any) {
    ElMessage.error(e?.message || '读取聊天记录失败')
  } finally {
    messageLoading.value = false
  }
}

/**
 * 用一次接口返回的会话详情刷新界面。
 *
 * 打开会话与导入文本返回的是同一种结构，因此共用这一段：
 * 会话列表里就地更新（新会话插到最前），中栏换成新的聊天记录。
 *
 * 会话列表同步更新，中栏换成最新聊天记录。
 */
async function applyDetail(detail: WechatChatDetail) {
  messages.value = detail.messages || []
  // 同步/导入后后端可能已经创建了会话或刷新了计数，用最新数据回填
  activeSession.value = detail.session
  acquaintanceDraft.value = detail.session.acquaintanceDate || ''
  mergeSessionIntoList(detail.session)
  dirtyCount.value = Math.max((detail.total || 0) - messages.value.length, 0)
  await scrollToBottom()
}

/** 打开导入文本对话框（每次都是干净的，不复用上一次粘贴的内容） */
function openImportDialog() {
  importForm.text = ''
  importDialog.value = true
}

/**
 * 确认粘贴后先解析昵称，不立即写库。
 */
async function confirmImport() {
  const text = importForm.text.trim()
  if (!text) {
    ElMessage.warning('请先粘贴聊天记录')
    return
  }
  importing.value = true
  try {
    const res = await previewWechatText({ text })
    const preview = res.data
    if (!preview) {
      ElMessage.error('解析失败：服务端没有返回昵称信息')
      return
    }
    pendingImport.value = preview
    const hint = preview.senders.find((sender) => ['me', '我', '自己'].includes(sender.label.trim().toLowerCase()))
    identityForm.selfLabel = hint?.label || ''
    importDialog.value = false
    identityDialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '文本解析失败')
  } finally {
    importing.value = false
  }
}

function disableFutureDate(date: Date) {
  const today = new Date()
  today.setHours(23, 59, 59, 999)
  return date.getTime() > today.getTime()
}

function openAcquaintanceDialog() {
  const item = activeSession.value
  if (!item?.sessionId) {
    ElMessage.warning('请先导入聊天记录')
    return
  }
  acquaintanceDraft.value = item.acquaintanceDate || ''
  acquaintanceDialog.value = true
}

/** 保存认识日期；清空日期时后端也会同步清除。 */
async function saveAcquaintanceDate() {
  const item = activeSession.value
  if (!item?.sessionId || acquaintanceSaving.value) {
    return
  }
  const nextDate = acquaintanceDraft.value || ''
  if (nextDate === (item.acquaintanceDate || '')) {
    acquaintanceDialog.value = false
    return
  }

  acquaintanceSaving.value = true
  try {
    const res = await updateWechatAcquaintanceDate({
      sessionId: item.sessionId,
      acquaintanceDate: nextDate
    })
    if (res.data) {
      activeSession.value = res.data
      acquaintanceDraft.value = res.data.acquaintanceDate || ''
      mergeSessionIntoList(res.data)
    }
    acquaintanceDialog.value = false
    ElMessage.success(nextDate ? '认识日期已保存' : '认识日期已清除')
  } catch (e: any) {
    acquaintanceDraft.value = item.acquaintanceDate || ''
    ElMessage.error(e?.message || '认识日期保存失败')
  } finally {
    acquaintanceSaving.value = false
  }
}

/** 手动同步记录 */
async function syncMessages() {
  const item = activeSession.value
  if (!item) {
    return
  }
  syncing.value = true
  try {
    const res = await syncWechatChat({
      sessionId: item.sessionId,
      chatUsername: item.chatUsername,
      displayName: item.displayName,
      group: item.group
    })
    if (res.data?.session) {
      activeSession.value = res.data.session
      mergeSessionIntoList(res.data.session)
    }
    await loadMessages(activeSession.value!)
    ElMessage.success('同步完成')
  } catch (e: any) {
    ElMessage.error(e?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

async function saveIdentity() {
  const preview = pendingImport.value
  if (!preview || !identityForm.selfLabel) {
    ElMessage.warning('请选择聊天记录中的自己')
    return
  }
  identitySaving.value = true
  try {
    const res = await commitWechatText({
      importId: preview.importId,
      selfLabel: identityForm.selfLabel
    })
    const detail = res.data
    if (!detail) {
      ElMessage.error('导入失败：服务端没有返回聊天记录')
      return
    }
    identityDialog.value = false
    pendingImport.value = null
    identityForm.selfLabel = ''
    await applyDetail(detail)
    ElMessage.success(`已导入 ${detail.total} 条消息`)
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    identitySaving.value = false
  }
}

function cancelIdentity() {
  identityDialog.value = false
  pendingImport.value = null
  identityForm.selfLabel = ''
}

async function removeSession(item: WechatSessionView) {
  if (!item.sessionId) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除会话“${item.displayName}”及其聊天记录吗？`, '删除会话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteWechatSession(item.sessionId)
    sessions.value = sessions.value.filter((session) => session.chatUsername !== item.chatUsername)
    if (activeSession.value?.chatUsername === item.chatUsername) {
      activeSession.value = null
      messages.value = []
      dirtyCount.value = 0
    }
    ElMessage.success('会话已删除')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(e?.message || '删除失败')
    }
  }
}

function mergeSessionIntoList(session: WechatSessionView) {
  const idx = sessions.value.findIndex((s: WechatSessionView) => s.chatUsername === session.chatUsername)
  if (idx >= 0) {
    sessions.value[idx] = session
  } else {
    sessions.value.unshift(session)
  }
}

function showDateDivider(index: number) {
  if (index === 0) {
    return true
  }
  return messages.value[index].date !== messages.value[index - 1].date
}

/**
 * 气泡上显示的名字。
 *
 * 已确认映射时用「我 / 对方」这类语义标签，未确认时退回原始标签，
 * 避免在没有依据的情况下替用户认定身份。
 */
function senderLabel(msg: WechatChatMessage) {
  if (msg.senderRole === 'SELF') {
    return selfLabel.value
  }
  if (msg.senderRole === 'TARGET') {
    const labels = activeSession.value?.targetLabels ?? []
    return labels.length ? labels[0] : msg.senderRaw || '对方'
  }
  return msg.senderRaw || '未知'
}

function bubbleClass(msg: WechatChatMessage) {
  return {
    // 语音与动画表情和图片一样按"媒体占位"渲染：斜体灰字，一眼看出不是文字
    media: msg.kind === 'MEDIA' || msg.kind === 'VOICE' || msg.kind === 'STICKER',
    quote: msg.kind === 'QUOTE'
  }
}

function shortName(name?: string | null) {
  if (!name) {
    return '?'
  }
  return name.length > 2 ? name.slice(-2) : name
}

/** 列表里显示成 04-01 10:23 这种紧凑格式 */
function shortTime(time?: string | null) {
  if (!time) {
    return ''
  }
  const m = time.match(/(\d{2}-\d{2})\s+(\d{2}:\d{2})/)
  if (m) {
    return m[1] + ' ' + m[2]
  }
  const d = time.match(/(\d{4}-\d{2}-\d{2})\s+(\d{2}:\d{2})/)
  return d ? d[1].slice(5) + ' ' + d[2] : time
}

function onlyTime(time?: string | null) {
  if (!time) {
    return ''
  }
  const m = time.match(/(\d{2}:\d{2})/)
  return m ? m[1] : time
}

function formatScore(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '--'
  }
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(1)
}

function formatPercent(value?: number | null) {
  return value === null || value === undefined ? '--' : `${formatScore(value)}%`
}

function affinityWidth(value?: number | null) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return '0%'
  }
  return `${Math.min(Math.max(Number(value), 0), 100)}%`
}

async function scrollToBottom() {
  await nextTick()
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}
</script>

<style scoped lang="scss">
@use 'sass:color';

/*
  取色统一走 color.adjust：全局的 darken() 已被 Dart Sass 标记为弃用，并会在 3.0 移除。
  color.adjust($c, $lightness: -N%) 与 darken($c, N%) 等价。
*/
$wechat-bg: #ededed;
$wechat-side: #e6e5e5;
$wechat-green: #95ec69;
$wechat-border: #d9d9d9;

.wechat-app {
  display: flex;
  height: calc(100vh - 84px);
  min-height: 520px;
  background: $wechat-bg;
  border: 1px solid $wechat-border;
  border-radius: 4px;
  overflow: hidden;
  font-size: 14px;
}

/* ---------------- 左侧会话列表 ---------------- */
.side-panel {
  width: 260px;
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  background: $wechat-side;
  border-right: 1px solid $wechat-border;
}

.side-search {
  padding: 10px;
  background: color.adjust($wechat-side, $lightness: -3%);
}

.side-body {
  flex: 1;
  overflow-y: auto;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: color.adjust($wechat-side, $lightness: -5%);
  }

  &.active {
    background: color.adjust($wechat-side, $lightness: -10%);
  }
}

.avatar {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
  border-radius: 4px;
  background: #4a90d9;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 500;

  &.group {
    background: #22a06b;
  }
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-top,
.session-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.session-name {
  font-size: 14px;
  color: #1a1a1a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-time {
  font-size: 11px;
  color: #9a9a9a;
  flex: 0 0 auto;
}

.session-delete {
  flex: 0 0 auto;
  padding: 0 2px;
  color: #a8abb2;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}

.session-item:hover .session-delete,
.session-delete:focus {
  opacity: 1;
}

.session-delete:hover {
  color: #f56c6c;
}

.session-preview {
  font-size: 12px;
  color: #9a9a9a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.badge {
  flex: 0 0 auto;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #f56c6c;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

/*
  左下角：数据来源入口 + 数据源状态。
  导入按钮占满整行，是这一角唯一的主操作；状态标签退到下面一行。
*/
.side-foot {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 10px;
  border-top: 1px solid $wechat-border;
}

/* tooltip 需要一个可包裹的元素，disabled 的按钮不触发鼠标事件 */
.import-wrap {
  display: block;
}

.import-btn {
  width: 100%;
}

.side-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

/* ---------------- 中间聊天区 ---------------- */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: $wechat-bg;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: #f7f7f7;
  border-bottom: 1px solid $wechat-border;
}

.chat-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-name {
  font-size: 16px;
  font-weight: 500;
  color: #1a1a1a;
}

.chat-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.relationship-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  row-gap: 8px;
  padding: 9px 16px;
  background: #fff;
  border-bottom: 1px solid $wechat-border;
}

.relationship-item {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.relationship-label {
  flex: 0 0 auto;
  font-size: 12px;
  color: #8a8a8a;
}

.relationship-value,
.performance-score {
  color: #303133;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;

  small {
    font-size: 11px;
    font-weight: 400;
  }
}

.affinity-item {
  flex: 0 0 170px;
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 8px;
}

.relationship-copy {
  display: flex;
  align-items: baseline;
  gap: 6px;
  grid-column: 1 / 3;
}

.affinity-track {
  grid-column: 1 / 3;
  height: 5px;
  margin-top: 4px;
  overflow: hidden;
  border-radius: 3px;
  background: #f0f2f5;

  span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: #e6a23c;
    transition: width 0.2s ease;
  }
}

.acquaintance-item {
  flex: 0 0 auto;
}

.acquaintance-link {
  padding: 0;
  color: #409eff;
  font-size: 13px;
}

.performance-item {
  margin-left: 4px;
}

.performance-score {
  color: #409eff;
}

.next-step-btn {
  margin-left: auto;
  flex: 0 0 auto;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.date-divider {
  text-align: center;
  margin: 12px 0;

  span {
    display: inline-block;
    padding: 2px 10px;
    border-radius: 10px;
    background: rgba(0, 0, 0, 0.08);
    color: #7a7a7a;
    font-size: 12px;
  }
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;

  &.self {
    flex-direction: row;
    justify-content: flex-end;
  }
}

.msg-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 4px;
  background: #22a06b;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.self-avatar {
  background: #4a90d9;
}

.msg-body {
  max-width: 62%;
  display: flex;
  flex-direction: column;
}

.msg-row.self .msg-body {
  align-items: flex-end;
}

.msg-sender {
  font-size: 11px;
  color: #9a9a9a;
  margin-bottom: 3px;
  padding-left: 2px;
}

.msg-bubble {
  position: relative;
  padding: 9px 12px;
  border-radius: 4px;
  background: #fff;
  color: #1a1a1a;
  line-height: 1.55;
  word-break: break-word;
  white-space: pre-wrap;

  &.media {
    font-style: italic;
    color: #7a7a7a;
  }

  &.quote {
    border-left: 3px solid #c9c9c9;
  }
}

.msg-row.self .msg-bubble {
  background: $wechat-green;
}

.msg-insights {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 6px;
}

.insight-line,
.reply-rating {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  line-height: 20px;
}

.insight-label {
  color: #8a8a8a;
}

.insight-percent {
  color: #606266;
  font-variant-numeric: tabular-nums;
}

.reply-rating {
  margin-top: 6px;
}

.rating-pending {
  color: #a8abb2;
}

.reply-score {
  color: #409eff;
  font-variant-numeric: tabular-nums;
}

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: 11px;
  color: #a5a5a5;
}

.dlg-alert {
  margin-bottom: 14px;
}

.next-step-content {
  color: #4a4a4a;
  font-size: 14px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.import-format {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
}

.import-textarea {
  font-family: Consolas, 'Courier New', monospace;
}

.acquaintance-dialog-picker {
  width: 100%;
}

.conversation-preview {
  color: #303133;
  font-weight: 600;
}

.full-width {
  width: 100%;
}
</style>
