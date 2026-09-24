<template>
  <div class="customer-service-page">
    <section class="service-shell">
      <header class="service-header">
        <div class="service-title">
          <div class="service-mark">
            <el-icon><Service /></el-icon>
          </div>
          <div class="service-heading">
            <div class="service-name-row">
              <h2>AI对话</h2>
              <span class="status-dot" :class="{ online: isReady }"></span>
              <span class="status-text">{{ statusText }}</span>
            </div>
            <div class="service-meta">
              <span>{{ currentChatModelName }}</span>
              <span class="meta-divider"></span>
              <span>{{ config.collectionIds.length }} 个知识库</span>
              <span class="meta-divider"></span>
              <span>TopK {{ config.topK }}</span>
            </div>
          </div>
        </div>

        <el-button
          icon="Setting"
          :disabled="!canEdit"
          v-hasPermi="['ai:chat:edit']"
          @click="openSettings"
        >
          设置
        </el-button>
      </header>

      <el-alert
        v-if="!isReady"
        class="setup-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="isConfigured ? 'AI对话已停用' : '请先完成AI对话设置'"
      />

      <main ref="messageListRef" class="message-list" v-loading="loading">
        <div v-if="messages.length === 0 && !loading" class="empty-state">
          <div class="empty-mark">
            <el-icon><ChatDotRound /></el-icon>
          </div>
          <h3>有什么可以帮你的？</h3>
        </div>

        <div
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div v-if="message.role === 'assistant'" class="message-avatar assistant-avatar">
            <el-icon><Service /></el-icon>
          </div>

          <div class="message-main">
            <div v-if="message.role === 'assistant'" class="message-insights">
              <el-tag v-if="message.intent" size="small" effect="plain">
                {{ message.intent }}
              </el-tag>
              <span v-if="message.intentConfidence !== undefined" class="confidence">
                {{ formatConfidence(message.intentConfidence) }}
              </span>
              <el-tag v-if="message.needsClarification" size="small" type="warning" effect="plain">
                需要补充信息
              </el-tag>
            </div>

            <div class="message-bubble" :class="{ clarification: message.needsClarification }">
              {{ message.content }}
            </div>

            <el-collapse v-if="message.sources?.length" class="source-list">
              <el-collapse-item :name="message.id">
                <template #title>
                  <span class="source-title">
                    <el-icon><Collection /></el-icon>
                    知识来源
                    <span class="source-count">{{ message.sources.length }}</span>
                  </span>
                </template>
                <div
                  v-for="(source, index) in message.sources"
                  :key="source.id"
                  class="source-item"
                >
                  <div class="source-head">
                    <span class="source-index">{{ index + 1 }}</span>
                    <span class="source-name">{{ sourceName(source) }}</span>
                  </div>
                  <div class="source-content">{{ source.content }}</div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>

          <div v-if="message.role === 'user'" class="message-avatar user-avatar">
            <el-icon><UserFilled /></el-icon>
          </div>
        </div>

        <div v-if="sending" class="message-row assistant">
          <div class="message-avatar assistant-avatar">
            <el-icon><Service /></el-icon>
          </div>
          <div class="typing">
            <span></span>
            <span></span>
            <span></span>
          </div>
        </div>
      </main>

      <footer class="composer">
        <div class="composer-box">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            resize="none"
            placeholder="输入你的问题"
            :disabled="loading || sending"
            @keydown="handleComposerKeydown"
          />
          <el-button
            class="send-button"
            type="primary"
            icon="Promotion"
            :loading="sending"
            :disabled="!draft.trim() || loading || !isReady"
            @click="sendMessage"
          >
            发送
          </el-button>
        </div>
      </footer>
    </section>

    <el-dialog v-model="settingsOpen" title="AI对话设置" width="640px" append-to-body>
      <el-form
        ref="settingsRef"
        :model="settingsForm"
        :rules="settingsRules"
        label-width="104px"
      >
        <el-form-item label="对话模型" prop="chatModelId">
          <el-select v-model="settingsForm.chatModelId" filterable class="full-width">
            <el-option
              v-for="model in options.chatModels"
              :key="model.modelId"
              :label="modelLabel(model)"
              :value="model.modelId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="向量模型" prop="embeddingModelId">
          <el-select v-model="settingsForm.embeddingModelId" filterable class="full-width">
            <el-option
              v-for="model in options.embeddingModels"
              :key="model.modelId"
              :label="modelLabel(model)"
              :value="model.modelId"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="知识库" prop="collectionIds">
          <el-select
            v-model="settingsForm.collectionIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            class="full-width"
            placeholder="请选择知识库"
          >
            <el-option
              v-for="collection in options.collections"
              :key="collection.id"
              :label="collection.name"
              :value="collection.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="TopK" prop="topK">
          <el-input-number v-model="settingsForm.topK" :min="1" :max="20" />
        </el-form-item>

        <el-form-item label="启用">
          <el-switch v-model="settingsForm.enabled" active-value="1" inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="settingsOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveSettings">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts" name="AiChat">
import type {
  AiModelConfig,
  CustomerServiceChatResponse,
  CustomerServiceChatTurn,
  CustomerServiceConfig,
  CustomerServiceOptions,
  CustomerServiceSource
} from '@/types'
import {
  chatWithCustomerService,
  getCustomerServiceConfig,
  getCustomerServiceOptions,
  updateCustomerServiceConfig
} from '@/api/knowledge/customerService'

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  intentConfidence?: number
  needsClarification?: boolean
  sources?: CustomerServiceSource[]
}

const { proxy } = getCurrentInstance() as any
const loading = ref(true)
const sending = ref(false)
const saving = ref(false)
const settingsOpen = ref(false)
const messageListRef = ref<HTMLElement>()
const settingsRef = ref()
const draft = ref('')
const messages = ref<ChatMessage[]>([])

const defaultConfig = (): CustomerServiceConfig => ({
  collectionIds: [],
  topK: 5,
  enabled: '1'
})

const config = ref<CustomerServiceConfig>(defaultConfig())
const options = ref<CustomerServiceOptions>({
  chatModels: [],
  embeddingModels: [],
  collections: []
})
const settingsForm = reactive<CustomerServiceConfig>(defaultConfig())

const settingsRules = {
  chatModelId: [{ required: true, message: '请选择对话模型', trigger: 'change' }],
  embeddingModelId: [{ required: true, message: '请选择向量模型', trigger: 'change' }],
  collectionIds: [{ required: true, type: 'array', min: 1, message: '请选择知识库集合', trigger: 'change' }],
  topK: [{ required: true, message: '请输入 TopK', trigger: 'change' }]
}

const canEdit = computed(() => proxy.$auth.hasPermi('ai:chat:edit'))
const isConfigured = computed(
  () =>
    config.value.chatModelId !== undefined &&
    config.value.embeddingModelId !== undefined &&
    config.value.collectionIds.length > 0
)
const isReady = computed(() => isConfigured.value && config.value.enabled === '1')
const statusText = computed(() => (!isConfigured.value ? '待配置' : config.value.enabled === '1' ? '在线' : '已停用'))
const currentChatModelName = computed(() => {
  const model = options.value.chatModels.find((item: AiModelConfig) => item.modelId === config.value.chatModelId)
  return model?.modelName || '未选择模型'
})

async function loadData() {
  loading.value = true
  try {
    const [configResponse, optionsResponse] = await Promise.all([
      getCustomerServiceConfig(),
      getCustomerServiceOptions()
    ])
    config.value = configResponse.data || defaultConfig()
    options.value = optionsResponse.data || {
      chatModels: [],
      embeddingModels: [],
      collections: []
    }
    if (!isConfigured.value && canEdit.value) {
      openSettings()
    }
  } finally {
    loading.value = false
  }
}

function openSettings() {
  Object.assign(settingsForm, {
    ...config.value,
    collectionIds: [...config.value.collectionIds]
  })
  settingsOpen.value = true
}

function saveSettings() {
  settingsRef.value?.validate(async (valid: boolean) => {
    if (!valid) return
    saving.value = true
    try {
      const response = await updateCustomerServiceConfig(settingsForm)
      config.value = response.data || { ...settingsForm, collectionIds: [...settingsForm.collectionIds] }
      settingsOpen.value = false
      proxy.$modal.msgSuccess('设置已保存')
    } finally {
      saving.value = false
    }
  })
}

async function sendMessage() {
  const question = draft.value.trim()
  if (!question || sending.value || loading.value) {
    return
  }
  if (!isReady.value) {
    if (canEdit.value) {
      if (isConfigured.value) {
        proxy.$modal.msgError('AI对话已停用')
      } else {
        openSettings()
      }
    } else {
      proxy.$modal.msgError(isConfigured.value ? 'AI对话已停用' : 'AI对话尚未完成设置')
    }
    return
  }

  const history = buildHistory()
  messages.value.push({
    id: nextMessageId(),
    role: 'user',
    content: question
  })
  draft.value = ''
  sending.value = true
  await scrollToBottom()

  try {
    const response = await chatWithCustomerService({ question, history })
    appendAssistantMessage(response.data)
  } finally {
    sending.value = false
    await scrollToBottom()
  }
}

function buildHistory(): CustomerServiceChatTurn[] {
  return messages.value.slice(-10).map((message: ChatMessage) => ({
    role: message.role,
    content: message.content
  }))
}

function appendAssistantMessage(data?: CustomerServiceChatResponse) {
  if (!data) return
  messages.value.push({
    id: nextMessageId(),
    role: 'assistant',
    content: data.answer,
    intent: data.intent,
    intentConfidence: data.intentConfidence,
    needsClarification: data.needsClarification,
    sources: data.sources || []
  })
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    sendMessage()
  }
}

function sourceName(source: CustomerServiceSource) {
  const documentName = source.documentName || '知识片段'
  const chunk = source.chunkIndex == null ? '' : ` · #${source.chunkIndex + 1}`
  const collection = source.collectionName ? ` · ${source.collectionName}` : ''
  return `${documentName}${chunk}${collection}`
}

function modelLabel(model: AiModelConfig) {
  return model.modelCode ? `${model.modelName} · ${model.modelCode}` : model.modelName || '未命名模型'
}

function formatConfidence(value: number) {
  return `${Math.round(value * 100)}%`
}

function nextMessageId() {
  return Date.now() + Math.floor(Math.random() * 1000)
}

async function scrollToBottom() {
  await nextTick()
  const element = messageListRef.value
  if (element) {
    element.scrollTop = element.scrollHeight
  }
}

loadData()
</script>

<style scoped lang="scss">
.customer-service-page {
  height: calc(100vh - 84px);
  min-height: 560px;
}

.service-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.service-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}

.service-title {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.service-mark {
  width: 40px;
  height: 40px;
  flex: 0 0 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #e8f1ff;
  color: #2f6bd8;
  font-size: 22px;
}

.service-heading {
  min-width: 0;
}

.service-name-row {
  display: flex;
  align-items: center;
  gap: 7px;
}

.service-name-row h2 {
  margin: 0;
  font-size: 18px;
  line-height: 24px;
  color: var(--el-text-color-primary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-warning);
}

.status-dot.online {
  background: var(--el-color-success);
}

.status-text,
.service-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.service-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta-divider {
  width: 1px;
  height: 10px;
  background: var(--el-border-color);
}

.setup-alert {
  border-radius: 0;
  border-width: 0 0 1px;
}

.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 24px clamp(16px, 5vw, 72px);
  background: #f5f7fa;
}

.empty-state {
  height: 100%;
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}

.empty-mark {
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dbe7fb;
  border-radius: 8px;
  background: #f2f7ff;
  color: #3b78df;
  font-size: 30px;
}

.empty-state h3 {
  margin: 16px 0 0;
  font-size: 17px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 22px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-avatar {
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  font-size: 17px;
}

.assistant-avatar {
  background: #e8f1ff;
  color: #2f6bd8;
}

.user-avatar {
  background: #e9f7ef;
  color: #2f8b57;
}

.message-main {
  max-width: min(760px, calc(100% - 54px));
}

.message-insights {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 22px;
  margin-bottom: 5px;
}

.confidence {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.message-bubble {
  padding: 11px 14px;
  border: 1px solid #e1e6ee;
  border-radius: 8px;
  background: #fff;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-row.user .message-bubble {
  border-color: #cfe0ff;
  background: #e9f1ff;
}

.message-bubble.clarification {
  border-color: #f2d7a6;
  background: #fff9ed;
}

.source-list {
  margin-top: 8px;
  border: 0;

  :deep(.el-collapse-item__header),
  :deep(.el-collapse-item__wrap) {
    border: 0;
    background: transparent;
  }

  :deep(.el-collapse-item__header) {
    height: 30px;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  :deep(.el-collapse-item__content) {
    padding-bottom: 0;
  }
}

.source-title {
  display: flex;
  align-items: center;
  gap: 5px;
}

.source-count {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: var(--el-fill-color);
  text-align: center;
  line-height: 18px;
}

.source-item {
  margin-top: 8px;
  padding: 10px 12px;
  border-left: 3px solid #9fbdEE;
  background: rgba(239, 245, 255, 0.72);
}

.source-head {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 5px;
}

.source-index {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  border-radius: 4px;
  background: #3b78df;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.source-name {
  color: var(--el-text-color-primary);
  font-size: 12px;
  font-weight: 500;
}

.source-content {
  max-height: 120px;
  overflow-y: auto;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.typing {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 38px;
  padding: 0 14px;
  border: 1px solid #e1e6ee;
  border-radius: 8px;
  background: #fff;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: typing 1.1s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.15s;
}

.typing span:nth-child(3) {
  animation-delay: 0.3s;
}

.composer {
  padding: 12px 18px 16px;
  border-top: 1px solid var(--el-border-color-light);
  background: #fff;
}

.composer-box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  max-width: 980px;
  margin: 0 auto;
}

.composer-box :deep(.el-textarea__inner) {
  min-height: 40px !important;
  padding: 9px 12px;
  border-radius: 6px;
  box-shadow: 0 0 0 1px var(--el-border-color) inset;
}

.send-button {
  width: 92px;
  height: 40px;
  flex: 0 0 92px;
}

.full-width {
  width: 100%;
}

@keyframes typing {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.45;
  }

  30% {
    transform: translateY(-3px);
    opacity: 1;
  }
}

@media (max-width: 720px) {
  .customer-service-page {
    height: calc(100vh - 76px);
    min-height: 500px;
  }

  .service-header {
    align-items: flex-start;
    padding: 12px;
  }

  .service-meta {
    max-width: 210px;
  }

  .message-list {
    padding: 18px 12px;
  }

  .message-main {
    max-width: calc(100% - 46px);
  }

  .composer {
    padding: 10px 12px 12px;
  }

  .composer-box {
    align-items: stretch;
  }

  .send-button {
    width: 74px;
    flex-basis: 74px;
  }
}
</style>
