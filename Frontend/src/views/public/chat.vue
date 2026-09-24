<template>
  <div class="public-chat">
    <header class="public-header">
      <router-link class="brand" to="/">
        <span class="brand-mark">
          <el-icon><ChatDotRound /></el-icon>
        </span>
        <span class="brand-name">WorkTable</span>
      </router-link>

      <nav class="scene-switcher" aria-label="AI 对话场景">
        <button
          v-for="item in scenes"
          :key="item.scene"
          type="button"
          :class="{ active: item.scene === scene }"
          @click="switchScene(item.scene)"
        >
          {{ item.title }}
        </button>
      </nav>

      <div class="header-actions">
        <el-button class="mobile-history-button" circle text @click="historyOpen = true">
          <el-icon><Clock /></el-icon>
        </el-button>
        <router-link class="admin-entry" to="/admin/login">后台管理</router-link>
      </div>
    </header>

    <div class="public-body">
      <aside class="history-sidebar">
        <el-button class="new-chat-button" @click="startNewConversation">
          <el-icon><EditPen /></el-icon>
          <span>新建对话</span>
        </el-button>

        <div class="history-heading">
          <span>历史对话</span>
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
            暂无对话记录
          </div>
          <button
            v-for="item in conversations"
            :key="item.conversationId"
            type="button"
            class="history-item"
            :class="{ active: item.conversationId === activeConversationId }"
            @click="openConversation(item.conversationId)"
          >
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
          <el-icon><EditPen /></el-icon>
          <span>新建对话</span>
        </el-button>
        <div class="history-heading"><span>历史对话</span></div>
        <div class="history-list">
          <div v-if="!conversations.length" class="history-empty">暂无对话记录</div>
          <button
            v-for="item in conversations"
            :key="item.conversationId"
            type="button"
            class="history-item"
            :class="{ active: item.conversationId === activeConversationId }"
            @click="openConversation(item.conversationId)"
          >
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
  height: 100vh;
  min-height: 560px;
  overflow: hidden;
  background: #fff;
  color: #1f2328;
}

.public-header {
  position: relative;
  z-index: 20;
  height: 60px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid #eceff3;
  background: rgba(255, 255, 255, 0.96);
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  width: fit-content;
  color: #1f2328;
}

.brand-mark {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eef4ff;
  color: #3867d6;
  font-size: 17px;
}

.brand-name {
  font-size: 16px;
  font-weight: 650;
}

.scene-switcher {
  display: flex;
  align-items: center;
  gap: 4px;
  max-width: min(72vw, 760px);
  overflow-x: auto;
  padding: 4px;
  border-radius: 10px;
  background: #f4f5f7;
  scrollbar-width: none;
}

.scene-switcher::-webkit-scrollbar {
  display: none;
}

.scene-switcher button {
  min-width: 96px;
  height: 34px;
  padding: 0 18px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #69707a;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition: color 0.18s, background 0.18s, box-shadow 0.18s;
}

.scene-switcher button:hover {
  color: #1f2328;
}

.scene-switcher button.active {
  background: #fff;
  color: #1f2328;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(25, 35, 55, 0.12);
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.admin-entry {
  color: #69707a;
  font-size: 13px;
}

.admin-entry:hover {
  color: #3867d6;
}

.mobile-history-button {
  display: none;
}

.public-body {
  height: calc(100vh - 60px);
  display: grid;
  grid-template-columns: 262px minmax(0, 1fr);
}

.history-sidebar {
  min-width: 0;
  padding: 16px 12px;
  border-right: 1px solid #eef0f3;
  background: #f8f9fb;
}

.new-chat-button {
  width: 100%;
  height: 42px;
  justify-content: flex-start;
  gap: 8px;
  border-color: #dfe4eb;
  border-radius: 8px;
  background: #fff;
  color: #31353b;
}

.history-heading {
  height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 7px;
  color: #87909c;
  font-size: 12px;
}

.history-list {
  height: calc(100% - 84px);
  overflow-y: auto;
}

.history-empty {
  padding: 28px 8px;
  color: #a0a7b1;
  font-size: 13px;
  text-align: center;
}

.history-item {
  position: relative;
  width: 100%;
  height: 58px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 3px;
  padding: 8px 34px 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #30343a;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.history-item:hover,
.history-item.active {
  background: #eceff4;
}

.history-content {
  min-width: 0;
  flex: 1;
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
  font-size: 13px;
  font-weight: 500;
}

.history-preview,
.history-time {
  color: #949ba5;
  font-size: 11px;
}

.history-time {
  flex: 0 0 auto;
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
  background: #fff;
}

.message-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 34px 24px 150px;
}

.message-scroll.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding-bottom: 130px;
}

.welcome {
  max-width: 700px;
  padding: 0 20px;
  text-align: center;
}

.welcome-mark {
  width: 52px;
  height: 52px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 18px;
  border-radius: 14px;
  background: #edf3ff;
  color: #3867d6;
  font-size: 25px;
}

.welcome h1 {
  margin: 0 0 12px;
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 620;
  line-height: 1.25;
}

.welcome p {
  max-width: 580px;
  margin: 0 auto;
  color: #7b838e;
  font-size: 15px;
  line-height: 1.7;
}

.welcome .service-unavailable {
  color: #b15f23;
}

.message-column {
  width: min(820px, 100%);
  margin: 0 auto;
}

.message {
  margin-bottom: 28px;
}

.message.user {
  display: flex;
  justify-content: flex-end;
}

.user-bubble {
  max-width: min(620px, 86%);
  padding: 10px 15px;
  border-radius: 16px 16px 4px 16px;
  background: #f0f2f5;
  color: #25292f;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.assistant-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.assistant-avatar {
  width: 32px;
  height: 32px;
  flex: 0 0 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  background: #edf3ff;
  color: #3867d6;
  font-size: 17px;
}

.assistant-content {
  min-width: 0;
  flex: 1;
  padding-top: 3px;
}

.message-text {
  color: #292d33;
  font-size: 15px;
  line-height: 1.82;
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
    height: 32px;
    color: #7b838e;
    font-size: 12px;
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
  margin-top: 7px;
  padding: 10px 12px;
  border-left: 2px solid #a8bde8;
  background: #f7f9fc;
}

.source-name {
  margin-bottom: 5px;
  color: #4a5360;
  font-size: 12px;
  font-weight: 600;
}

.source-content {
  max-height: 130px;
  overflow-y: auto;
  color: #737c88;
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.typing {
  height: 30px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8e98a6;
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
  padding: 16px 24px 24px;
  background: linear-gradient(to bottom, rgba(255, 255, 255, 0), #fff 25%);
}

.composer-area.centered {
  position: fixed;
  right: 0;
  bottom: 16vh;
  left: 262px;
  padding: 0 24px;
  background: transparent;
}

.composer {
  width: min(820px, 100%);
  min-height: 56px;
  display: flex;
  align-items: flex-end;
  gap: 10px;
  margin: 0 auto;
  padding: 9px 10px 9px 16px;
  border: 1px solid #dce1e8;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 7px 24px rgba(24, 36, 55, 0.08);
}

.composer:focus-within {
  border-color: #9db9ed;
  box-shadow: 0 7px 24px rgba(51, 102, 204, 0.12);
}

.composer :deep(.el-textarea__inner) {
  min-height: 36px !important;
  padding: 7px 2px;
  border: 0;
  box-shadow: none !important;
  color: #25292f;
  font-size: 15px;
  line-height: 1.6;
}

.send-button {
  width: 38px;
  height: 38px;
  flex: 0 0 38px;
}

.mobile-history-panel {
  height: 100%;
  padding: 16px 12px;
  background: #f8f9fb;
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
    height: 58px;
    padding: 0 12px;
  }

  .brand-name,
  .admin-entry {
    display: none;
  }

  .scene-switcher {
    justify-self: center;
  }

  .scene-switcher button {
    min-width: auto;
    height: 32px;
    padding: 0 12px;
    font-size: 13px;
  }

  .mobile-history-button {
    display: inline-flex;
  }

  .public-body {
    height: calc(100vh - 58px);
    display: block;
  }

  .history-sidebar {
    display: none;
  }

  .message-scroll {
    padding: 24px 14px 142px;
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
    font-size: 24px;
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
    min-height: 52px;
    padding-left: 13px;
    border-radius: 12px;
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
