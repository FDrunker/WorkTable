<template>
  <div class="public-chat">
    <header class="public-header">
      <router-link class="brand" to="/">
        <span class="brand-mark">
          <el-icon><Grid /></el-icon>
        </span>
        <span class="brand-name">WorkTable</span>
      </router-link>

      <nav class="scene-switcher" aria-label="AI 对话场景">
        <button
          v-for="(item, index) in scenes"
          :key="item.scene"
          type="button"
          :class="{ active: item.scene === scene }"
          :aria-current="item.scene === scene ? 'page' : undefined"
          @click="switchScene(item.scene)"
        >
          <span class="scene-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span>{{ item.title }}</span>
        </button>
      </nav>

      <div class="header-actions">
        <el-tooltip content="会话历史" placement="bottom">
          <el-button
            class="mobile-history-button"
            circle
            text
            aria-label="打开会话历史"
            @click="historyOpen = true"
          >
            <el-icon><Clock /></el-icon>
          </el-button>
        </el-tooltip>
        <router-link class="admin-entry" to="/admin/login">后台管理</router-link>
      </div>
    </header>

    <div class="public-body">
      <aside class="history-sidebar">
        <el-button class="new-chat-button" @click="startNewConversation">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </el-button>

        <div class="history-heading">
          <span>会话索引</span>
          <span class="history-count">{{ String(conversations.length).padStart(2, '0') }}</span>
          <el-button
            v-if="conversations.length"
            text
            circle
            size="small"
            aria-label="刷新历史对话"
            @click="loadConversations"
          >
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <div v-loading="historyLoading" class="history-list">
          <div v-if="!conversations.length && !historyLoading" class="history-empty">
            <span>NO RECORDS</span>
            暂无对话记录
          </div>
          <button
            v-for="(item, index) in conversations"
            :key="item.conversationId"
            type="button"
            class="history-item"
            :class="{ active: item.conversationId === activeConversationId }"
            @click="openConversation(item.conversationId)"
          >
            <span class="history-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="history-content">
              <span class="history-title">{{ item.title || '新对话' }}</span>
              <span class="history-preview">{{ item.lastMessage || '等待开始对话' }}</span>
            </span>
            <span class="history-time">{{ formatHistoryTime(item.updateTime) }}</span>
            <el-button
              class="history-delete"
              text
              circle
              size="small"
              aria-label="删除对话"
              @click.stop="removeConversation(item.conversationId)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </button>
        </div>
      </aside>

      <main class="chat-main">
        <div ref="messageListRef" class="message-scroll" :class="{ empty: !messages.length }">
          <section v-if="!messages.length" class="welcome">
            <div class="welcome-mark">
              <el-icon><ChatDotRound /></el-icon>
            </div>
            <h1>{{ sceneTitle }}</h1>
            <p v-if="isReady">{{ sceneWelcome }}</p>
            <p v-else class="service-unavailable">
              当前服务暂不可用，请稍后再试
            </p>
          </section>

          <div v-else class="message-column">
            <article
              v-for="(message, index) in messages"
              :key="message.messageId || `${message.role}-${index}`"
              class="message"
              :class="message.role"
            >
              <div v-if="message.role === 'assistant'" class="assistant-row">
                <div class="assistant-avatar">
                  <el-icon><ChatDotRound /></el-icon>
                </div>
                <div class="assistant-content">
                  <div class="message-text">{{ message.content }}</div>
                  <el-collapse v-if="message.sources?.length" class="source-list">
                    <el-collapse-item :name="String(index)">
                      <template #title>
                        <span class="source-title">
                          <el-icon><Document /></el-icon>
                          知识来源
                          <span>{{ message.sources.length }}</span>
                        </span>
                      </template>
                      <div
                        v-for="(source, sourceIndex) in message.sources"
                        :key="source.id"
                        class="source-item"
                      >
                        <div class="source-name">
                          {{ sourceIndex + 1 }}. {{ source.documentName || source.collectionName || '知识片段' }}
                        </div>
                        <div class="source-content">{{ source.content }}</div>
                      </div>
                    </el-collapse-item>
                  </el-collapse>
                </div>
              </div>
              <div v-else class="user-bubble">{{ message.content }}</div>
            </article>

            <article v-if="sending" class="message assistant">
              <div class="assistant-row">
                <div class="assistant-avatar">
                  <el-icon><ChatDotRound /></el-icon>
                </div>
                <div class="typing" aria-label="正在生成回答">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </article>
          </div>
        </div>

        <div class="composer-area" :class="{ centered: !messages.length }">
          <div class="composer">
            <el-input
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 6 }"
              resize="none"
              :placeholder="isReady ? '给 AI 发送消息' : '当前服务暂不可用'"
              :disabled="!isReady || sending || loadingConversation"
              @keydown="handleComposerKeydown"
            />
            <el-button
              class="send-button"
              type="primary"
              circle
              :loading="sending"
              :disabled="!draft.trim() || !isReady || loadingConversation"
              aria-label="发送消息"
              @click="sendMessage"
            >
              <el-icon v-if="!sending"><Promotion /></el-icon>
            </el-button>
          </div>
        </div>
      </main>
    </div>

    <el-drawer
      v-model="historyOpen"
      class="mobile-history-drawer"
      direction="ltr"
      size="min(86vw, 320px)"
      :with-header="false"
    >
      <div class="mobile-history-panel">
        <el-button class="new-chat-button" @click="startNewConversation">
          <el-icon><Plus /></el-icon>
          <span>新建对话</span>
        </el-button>
        <div class="history-heading">
          <span>会话索引</span>
          <span class="history-count">{{ String(conversations.length).padStart(2, '0') }}</span>
        </div>
        <div class="history-list">
          <div v-if="!conversations.length" class="history-empty">暂无对话记录</div>
          <button
            v-for="(item, index) in conversations"
            :key="item.conversationId"
            type="button"
            class="history-item"
            :class="{ active: item.conversationId === activeConversationId }"
            @click="openConversation(item.conversationId)"
          >
            <span class="history-index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="history-content">
              <span class="history-title">{{ item.title || '新对话' }}</span>
              <span class="history-preview">{{ item.lastMessage || '等待开始对话' }}</span>
            </span>
          </button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ElMessageBox } from 'element-plus'
import {
  createPublicChatConversation,
  deletePublicChatConversation,
  getPublicChatBootstrap,
  getPublicChatConversation,
  listPublicChatScenes,
  sendPublicChatMessage
} from '@/api/publicChat'
import type {
  PublicChatConversation,
  PublicChatMessage,
  PublicChatScene
} from '@/types'

const props = defineProps<{
  scene: string
}>()

const fallbackScenes: PublicChatScene[] = [
  { scene: 'personal-info', title: '个人信息', welcomeMessage: '你好，我可以帮你整理和分析个人信息。' },
  { scene: 'customer-service', title: '智能客服', welcomeMessage: '你好，我是智能客服，请告诉我你需要咨询的问题。' }
]

const router = useRouter()
const scenes = ref<PublicChatScene[]>(fallbackScenes)
const conversations = ref<PublicChatConversation[]>([])
const messages = ref<PublicChatMessage[]>([])
const activeConversationId = ref<string>()
const sceneTitle = ref('')
const sceneWelcome = ref('')
const configured = ref(false)
const enabled = ref(false)
const draft = ref('')
const loadingConversation = ref(false)
const historyLoading = ref(false)
const sending = ref(false)
const historyOpen = ref(false)
const messageListRef = ref<HTMLElement>()
const visitorId = getVisitorId()
const activeConversationStorageKey = computed(() => `public-ai-active:${props.scene}`)

const scene = computed(() => props.scene)
const isReady = computed(() => configured.value && enabled.value)

watch(
  scene,
  async () => {
    await loadScene()
  },
  { immediate: true }
)

async function loadScenes() {
  try {
    const response = await listPublicChatScenes()
    if (response.data?.length) {
      scenes.value = response.data
    }
  } catch {
    scenes.value = fallbackScenes
  }
}

async function loadScene() {
  sending.value = false
  draft.value = ''
  messages.value = []
  conversations.value = []
  activeConversationId.value = undefined
  historyOpen.value = false

  const fallback = fallbackScenes.find((item) => item.scene === scene.value)
  sceneTitle.value = fallback?.title || 'AI 对话'
  sceneWelcome.value = fallback?.welcomeMessage || '你好，有什么可以帮你的？'

  historyLoading.value = true
  try {
    const response = await getPublicChatBootstrap(scene.value, visitorId)
    if (response.data) {
      sceneTitle.value = response.data.scene.title
      sceneWelcome.value = response.data.scene.welcomeMessage
      configured.value = response.data.configured
      enabled.value = response.data.enabled
      conversations.value = response.data.conversations || []
      restoreActiveConversation()
    }
  } finally {
    historyLoading.value = false
  }
}

async function loadConversations() {
  historyLoading.value = true
  try {
    const response = await getPublicChatBootstrap(scene.value, visitorId)
    if (response.data) {
      conversations.value = response.data.conversations || []
      configured.value = response.data.configured
      enabled.value = response.data.enabled
      sceneTitle.value = response.data.scene.title
      sceneWelcome.value = response.data.scene.welcomeMessage
    }
  } finally {
    historyLoading.value = false
  }
}

function restoreActiveConversation() {
  const conversationId = localStorage.getItem(activeConversationStorageKey.value)
  if (!conversationId || !conversations.value.some((item: PublicChatConversation) => item.conversationId === conversationId)) {
    localStorage.removeItem(activeConversationStorageKey.value)
    return
  }
  openConversation(conversationId)
}

function switchScene(targetScene: string) {
  if (targetScene === scene.value) return
  const path = targetScene === 'personal-info'
    ? '/'
    : targetScene === 'customer-service'
      ? '/customer-service'
      : `/chat/${targetScene}`
  router.push(path)
}

function startNewConversation() {
  activeConversationId.value = undefined
  messages.value = []
  draft.value = ''
  historyOpen.value = false
  localStorage.removeItem(activeConversationStorageKey.value)
  nextTick(focusComposer)
}

async function openConversation(conversationId: string) {
  if (conversationId === activeConversationId.value) {
    historyOpen.value = false
    return
  }
  loadingConversation.value = true
  historyOpen.value = false
  try {
    const response = await getPublicChatConversation(scene.value, conversationId, visitorId)
    if (!response.data) return
    activeConversationId.value = conversationId
    messages.value = response.data.messages || []
    localStorage.setItem(activeConversationStorageKey.value, conversationId)
    await scrollToBottom()
  } finally {
    loadingConversation.value = false
  }
}

async function removeConversation(conversationId: string) {
  await ElMessageBox.confirm('删除后无法恢复，确定删除这条对话吗？', '删除对话', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  })
  await deletePublicChatConversation(scene.value, conversationId, visitorId)
  conversations.value = conversations.value.filter(
    (item: PublicChatConversation) => item.conversationId !== conversationId
  )
  if (activeConversationId.value === conversationId) {
    startNewConversation()
  }
}

async function sendMessage() {
  const question = draft.value.trim()
  if (!question || sending.value || loadingConversation.value || !isReady.value) {
    return
  }

  sending.value = true
  const optimisticMessage: PublicChatMessage = { role: 'user', content: question }
  messages.value.push(optimisticMessage)
  draft.value = ''
  await scrollToBottom()

  try {
    let conversationId = activeConversationId.value
    if (!conversationId) {
      const createResponse = await createPublicChatConversation(scene.value, visitorId)
      if (!createResponse.data) {
        throw new Error('创建对话失败')
      }
      conversationId = createResponse.data.conversationId
      activeConversationId.value = conversationId
      localStorage.setItem(activeConversationStorageKey.value, conversationId)
    }

    const response = await sendPublicChatMessage(scene.value, conversationId, visitorId, question)
    if (!response.data) {
      throw new Error('发送消息失败')
    }
    messages.value.splice(-1, 1, response.data.userMessage, response.data.assistantMessage)
    await loadConversations()
    await scrollToBottom()
  } catch {
    messages.value = messages.value.filter((message: PublicChatMessage) => message !== optimisticMessage)
    draft.value = question
  } finally {
    sending.value = false
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    sendMessage()
  }
}

async function scrollToBottom() {
  await nextTick()
  const element = messageListRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

function focusComposer() {
  const textarea = document.querySelector<HTMLTextAreaElement>('.composer textarea')
  textarea?.focus()
}

function formatHistoryTime(value?: string) {
  if (!value) return ''
  const date = new Date(value.replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  if (date.toDateString() === now.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

function getVisitorId() {
  const key = 'public-ai-visitor-id'
  const existing = localStorage.getItem(key)
  if (existing) return existing
  const bytes = new Uint8Array(18)
  crypto.getRandomValues(bytes)
  const value = `visitor_${Array.from(bytes, (item) => item.toString(36).padStart(2, '0')).join('')}`
  localStorage.setItem(key, value)
  return value
}

loadScenes()
</script>

<style scoped lang="scss">
.public-chat {
  --ink: #18211e;
  --ink-soft: #4f5c57;
  --muted: #7c8782;
  --paper: #f3f5f0;
  --paper-raised: #fbfcf8;
  --line: #dce2da;
  --line-strong: #c8d0c7;
  --accent: #0b6a57;
  --accent-soft: #e4efe9;
  --signal: #ad5b28;
  height: 100vh;
  min-height: 560px;
  overflow: hidden;
  background: var(--paper-raised);
  color: var(--ink);
  font-family: "Microsoft YaHei", "PingFang SC", "Noto Sans SC", sans-serif;
}

.public-header {
  position: relative;
  z-index: 20;
  height: 64px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
  background: var(--paper-raised);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: fit-content;
  color: var(--ink);
}

.brand-mark {
  position: relative;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 3px;
  background: var(--ink);
  color: #f6faf6;
  font-size: 16px;
}

.brand-mark::after {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 8px;
  height: 8px;
  border: 2px solid var(--paper-raised);
  border-radius: 50%;
  background: var(--accent);
  content: "";
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0;
}

.scene-switcher {
  display: flex;
  align-self: stretch;
  align-items: flex-end;
  gap: 28px;
  max-width: min(72vw, 760px);
  overflow-x: auto;
  padding: 0;
  background: transparent;
  scrollbar-width: none;
}

.scene-switcher::-webkit-scrollbar {
  display: none;
}

.scene-switcher button {
  min-width: auto;
  height: 63px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px;
  border: 0;
  border-bottom: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--ink-soft);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.18s, border-color 0.18s;
}

.scene-switcher button:hover {
  color: var(--ink);
}

.scene-switcher button.active {
  border-bottom-color: var(--accent);
  color: var(--ink);
  font-weight: 700;
}

.scene-index {
  color: var(--muted);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-weight: 500;
}

.scene-switcher button.active .scene-index {
  color: var(--accent);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.admin-entry {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 13px;
}

.admin-entry:hover {
  border-bottom-color: var(--accent);
  color: var(--accent);
}

.mobile-history-button {
  display: none;
}

.public-body {
  height: calc(100vh - 64px);
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
}

.history-sidebar {
  min-width: 0;
  padding: 18px 14px;
  border-right: 1px solid var(--line);
  background: var(--paper);
}

.new-chat-button {
  width: 100%;
  height: 40px;
  justify-content: flex-start;
  gap: 8px;
  border-color: var(--ink);
  border-radius: 4px;
  background: var(--ink);
  color: #f7faf7;
  font-weight: 600;
}

.new-chat-button:hover,
.new-chat-button:focus {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
}

.history-heading {
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  letter-spacing: 0;
  text-transform: uppercase;
}

.history-count {
  color: var(--accent);
  font-variant-numeric: tabular-nums;
}

.history-list {
  height: calc(100% - 86px);
  padding-top: 8px;
  overflow-y: auto;
}

.history-empty {
  padding: 32px 6px;
  color: var(--muted);
  font-size: 12px;
  text-align: left;
}

.history-empty span {
  display: block;
  margin-bottom: 4px;
  color: var(--line-strong);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.history-item {
  position: relative;
  width: 100%;
  height: 58px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 1px;
  padding: 8px 30px 8px 12px;
  border-top: 1px solid transparent;
  border-bottom: 1px solid transparent;
  border-left: 2px solid transparent;
  border-radius: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.history-item:hover {
  border-bottom-color: var(--line);
  background: rgba(255, 255, 255, 0.54);
}

.history-item.active {
  border-left-color: var(--accent);
  border-bottom-color: var(--line);
  background: var(--accent-soft);
}

.history-content {
  min-width: 0;
  flex: 1;
}

.history-index {
  width: 18px;
  flex: 0 0 18px;
  color: var(--line-strong);
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
  font-size: 9px;
  font-variant-numeric: tabular-nums;
}

.history-item.active .history-index {
  color: var(--accent);
}

.history-title,
.history-preview {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-title {
  margin-bottom: 3px;
  font-size: 12px;
  font-weight: 650;
}

.history-preview,
.history-time {
  color: var(--muted);
  font-size: 10px;
}

.history-time {
  flex: 0 0 auto;
  font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
}

.history-delete {
  position: absolute;
  right: 4px;
  opacity: 0;
}

.history-item:hover .history-delete {
  opacity: 1;
}

.history-item:hover .history-time {
  opacity: 0;
}

.chat-main {
  position: relative;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--paper-raised);
}

.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 38px 28px 164px;
}

.message-scroll.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 130px;
}

.welcome {
  position: relative;
  max-width: 660px;
  padding: 0 20px;
  text-align: center;
}

.welcome-mark {
  position: relative;
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  border: 1px solid var(--ink);
  border-radius: 4px;
  background: var(--ink);
  color: #f7faf7;
  font-size: 22px;
}

.welcome-mark::after {
  position: absolute;
  right: -5px;
  bottom: 10px;
  width: 10px;
  height: 2px;
  background: var(--accent);
  content: "";
}

.welcome h1 {
  margin: 0 0 12px;
  color: var(--ink);
  font-family: "Songti SC", "Noto Serif SC", "SimSun", serif;
  font-size: clamp(28px, 3vw, 38px);
  font-weight: 650;
  line-height: 1.2;
}

.welcome p {
  max-width: 580px;
  margin: 0 auto;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.8;
}

.welcome .service-unavailable {
  color: var(--signal);
}

.message-column {
  width: min(840px, 100%);
  margin: 0 auto;
}

.message {
  margin-bottom: 30px;
}

.message.user {
  display: flex;
  justify-content: flex-end;
}

.user-bubble {
  max-width: min(620px, 86%);
  padding: 11px 14px;
  border: 1px solid #cfd9d1;
  border-radius: 6px 6px 2px 6px;
  background: var(--accent-soft);
  color: var(--ink);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.assistant-avatar {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: var(--paper-raised);
  color: var(--accent);
  font-size: 15px;
}

.assistant-content {
  min-width: 0;
  flex: 1;
  padding-top: 3px;
}

.message-text {
  color: var(--ink);
  font-size: 14px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.source-list {
  margin-top: 10px;
  border: 0;

  :deep(.el-collapse-item__header),
  :deep(.el-collapse-item__wrap) {
    border: 0;
    background: transparent;
  }

  :deep(.el-collapse-item__header) {
    height: 34px;
    color: var(--ink-soft);
    font-family: "Cascadia Mono", "SFMono-Regular", Consolas, monospace;
    font-size: 10px;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 0;
  }
}

.source-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.source-item {
  margin-top: 8px;
  padding: 10px 12px;
  border-left: 2px solid var(--accent);
  background: var(--paper);
}

.source-name {
  margin-bottom: 5px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 700;
}

.source-content {
  max-height: 130px;
  overflow-y: auto;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.typing {
  height: 30px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.typing span {
  width: 8px;
  height: 3px;
  border-radius: 0;
  background: var(--accent);
  animation: public-typing 1.1s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.composer-area {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 14px 24px 24px;
  border-top: 1px solid var(--line);
  background: var(--paper-raised);
}

.composer-area.centered {
  position: fixed;
  right: 0;
  bottom: 16vh;
  left: 248px;
  padding: 0 24px;
  border-top: 0;
  background: transparent;
}

.composer {
  width: min(840px, 100%);
  min-height: 54px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 0 auto;
  padding: 8px 9px 8px 15px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 1px 0 rgba(24, 33, 30, 0.06);
}

.composer:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
}

.composer :deep(.el-textarea__inner) {
  min-height: 36px !important;
  padding: 7px 2px;
  border: 0;
  box-shadow: none !important;
  color: var(--ink);
  font-size: 14px;
  line-height: 1.6;
}

.send-button {
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 4px;
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-hover-bg-color: var(--ink);
  --el-button-hover-border-color: var(--ink);
  --el-button-active-bg-color: var(--ink);
  --el-button-active-border-color: var(--ink);
  --el-button-disabled-bg-color: var(--line);
  --el-button-disabled-border-color: var(--line);
  --el-button-disabled-text-color: var(--muted);
}

.mobile-history-panel {
  height: 100%;
  padding: 18px 14px;
  background: var(--paper);
}

.mobile-history-panel .history-list {
  height: calc(100% - 84px);
}

@keyframes public-typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.42;
  }

  30% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

@media (max-width: 820px) {
  .public-header {
    grid-template-columns: auto 1fr auto;
    gap: 10px;
    height: 60px;
    padding: 0 12px;
  }

  .brand-name,
  .admin-entry {
    display: none;
  }

  .scene-switcher {
    justify-self: center;
    gap: 18px;
  }

  .scene-switcher button {
    min-width: auto;
    height: 59px;
    padding: 0 1px;
    font-size: 13px;
  }

  .scene-index {
    display: none;
  }

  .mobile-history-button {
    display: inline-flex;
  }

  .public-body {
    height: calc(100vh - 60px);
    display: block;
  }

  .history-sidebar {
    display: none;
  }

  .message-scroll {
    padding: 28px 14px 148px;
  }

  .message-scroll.empty {
    padding-bottom: 120px;
  }

  .welcome {
    padding: 0 10px;
  }

  .welcome-mark {
    width: 46px;
    height: 46px;
    margin-bottom: 14px;
    font-size: 22px;
  }

  .welcome h1 {
    font-size: 26px;
  }

  .composer-area {
    padding: 12px 12px 16px;
  }

  .composer-area.centered {
    bottom: 12vh;
    left: 0;
    padding: 0 12px;
  }

  .composer {
    min-height: 50px;
    padding-left: 13px;
    border-radius: 5px;
  }

  .assistant-row {
    gap: 9px;
  }

  .assistant-avatar {
    width: 30px;
    height: 30px;
    flex-basis: 30px;
  }

  .message-text,
  .user-bubble {
    font-size: 14px;
  }
}
</style>
