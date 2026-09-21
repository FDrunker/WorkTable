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
            </div>
            <div class="session-bottom">
              <span class="session-preview">{{ item.lastMessage || '暂无归档消息' }}</span>
              <span v-if="item.unanalyzedCount > 0" class="badge">{{ item.unanalyzedCount }}</span>
            </div>
          </div>
        </div>

        <el-empty v-if="!sessionLoading && filteredSessions.length === 0" description="没有会话" :image-size="80" />
      </div>

      <div class="side-foot">
        <el-tooltip :content="statusText" placement="top">
          <el-tag :type="statusReady ? 'success' : 'warning'" size="small" effect="plain">
            {{ statusReady ? '数据源就绪' : '数据源未就绪' }}
          </el-tag>
        </el-tooltip>
        <el-button link size="small" @click="loadStatus">
          <el-icon><Refresh /></el-icon>
        </el-button>
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
            <el-button type="primary" size="small" :loading="analyzing" @click="runAnalysis">
              <el-icon><MagicStick /></el-icon>
              <span>分析</span>
              <span v-if="unanalyzedCount > 0" class="btn-badge">{{ unanalyzedCount }}</span>
            </el-button>
          </div>
        </header>

        <!-- 说话人映射确认：军师 skill 要求映射不明时必须先问 -->
        <el-alert
          v-if="!activeSession.identityConfirmed"
          class="identity-alert"
          type="warning"
          :closable="false"
          show-icon
        >
          <template #title>
            尚未确认「我」是哪个昵称
            <el-button link type="primary" size="small" @click="identityDialog = true">立即确认</el-button>
          </template>
          <div class="identity-hint">
            未确认时消息只能显示原始标签，军师也无法正确判断谁在主动。当前推断：我 =
            {{ activeSession.selfLabels.length ? activeSession.selfLabels.join('、') : '未知' }}；对方 =
            {{ activeSession.targetLabels.length ? activeSession.targetLabels.join('、') : '未知' }}
          </div>
        </el-alert>

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
                <div class="msg-meta">
                  <span class="msg-time">{{ onlyTime(msg.time) }}</span>
                  <span v-if="!msg.analyzed" class="msg-dirty">未分析</span>
                  <span v-else class="msg-clean">已分析</span>
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

    <!-- ============ 右栏：分析结果 / 历史分析 ============ -->
    <aside class="analysis-panel">
      <div class="analysis-head">
        <span class="panel-title">军师分析</span>
        <el-button
          v-if="activeSession && activeSession.sessionId"
          link
          size="small"
          :loading="historyLoading"
          @click="loadHistory"
        >
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>

      <div class="analysis-goal">
        <el-input
          v-model="goal"
          type="textarea"
          :rows="2"
          resize="none"
          maxlength="500"
          show-word-limit
          placeholder="想解决什么？（可留空，例如：这句怎么回 / 对方还有没有兴趣）"
        />
        <div class="goal-actions">
          <span class="goal-hint">
            <template v-if="unanalyzedCount > 0">待分析 {{ unanalyzedCount }} 条</template>
            <template v-else>没有未分析的记录</template>
          </span>
          <el-button
            type="primary"
            size="small"
            :disabled="!activeSession || !activeSession.sessionId || unanalyzedCount === 0 || analyzing"
            :loading="analyzing"
            @click="runAnalysis"
          >
            分析未分析记录
          </el-button>
        </div>
      </div>

      <div class="analysis-body" v-loading="historyLoading">
        <el-empty
          v-if="!historyLoading && analysisList.length === 0"
          description="还没有分析记录，点击上方按钮开始"
          :image-size="70"
        />

        <div v-for="item in analysisList" :key="item.analysisId" class="analysis-card">
          <div class="card-head" @click="toggleAnalysis(item.analysisId)">
            <div class="card-head-left">
              <el-tag v-if="item.crisis === '1'" type="danger" size="small" effect="dark">安全提示</el-tag>
              <el-tag v-else-if="item.status === '1'" type="info" size="small">失败</el-tag>
              <el-tag v-else type="success" size="small" effect="plain">已完成</el-tag>
              <span class="card-count">{{ item.messageCount }} 条</span>
            </div>
            <div class="card-head-right">
              <span class="card-time">{{ shortTime(item.createTime) }}</span>
              <el-icon><ArrowDown v-if="expandedId === item.analysisId" /><ArrowRight v-else /></el-icon>
            </div>
          </div>

          <div v-if="item.goal" class="card-goal">诉求：{{ item.goal }}</div>

          <div v-if="expandedId === item.analysisId" class="card-detail">
            <!-- 失败记录直接展示原因，不要给用户看空结构 -->
            <el-alert v-if="item.status === '1'" type="error" :closable="false" :title="item.errorMsg || '分析失败'" />

            <!-- 命中安全红线时展示固定指引，不套用恋爱话术 -->
            <div v-else-if="item.crisis === '1'" class="crisis-box">{{ item.crisisMessage }}</div>

            <template v-else-if="parseResult(item)">
              <section v-if="parseResult(item)!.emotionLanding" class="sec">
                <h4>情绪落地</h4>
                <p>{{ parseResult(item)!.emotionLanding }}</p>
              </section>

              <section v-if="parseResult(item)!.facts" class="sec">
                <h4>事实拆分</h4>
                <div v-if="parseResult(item)!.facts!.knownFacts.length" class="fact-group">
                  <span class="fact-title known">已知事实</span>
                  <ul>
                    <li v-for="(f, i) in parseResult(item)!.facts!.knownFacts" :key="'k' + i">{{ f }}</li>
                  </ul>
                </div>
                <div v-if="parseResult(item)!.facts!.inferences.length" class="fact-group">
                  <span class="fact-title infer">推测</span>
                  <ul>
                    <li v-for="(f, i) in parseResult(item)!.facts!.inferences" :key="'i' + i">{{ f }}</li>
                  </ul>
                </div>
                <div v-if="parseResult(item)!.facts!.keyUnknowns.length" class="fact-group">
                  <span class="fact-title unknown">关键未知</span>
                  <ul>
                    <li v-for="(f, i) in parseResult(item)!.facts!.keyUnknowns" :key="'u' + i">{{ f }}</li>
                  </ul>
                </div>
              </section>

              <section v-if="parseResult(item)!.interest" class="sec">
                <h4>利益判断</h4>
                <p>{{ parseResult(item)!.interest!.evaluation }}</p>
              </section>

              <section v-if="parseResult(item)!.advice" class="sec">
                <h4>明确建议</h4>
                <p class="primary-advice">{{ parseResult(item)!.advice!.primary }}</p>
                <ul v-if="parseResult(item)!.advice!.reasons.length" class="reason-list">
                  <li v-for="(r, i) in parseResult(item)!.advice!.reasons" :key="'r' + i">{{ r }}</li>
                </ul>
                <div v-if="parseResult(item)!.advice!.steadyVariant" class="variant">
                  <span class="variant-tag">稳健版</span>{{ parseResult(item)!.advice!.steadyVariant }}
                </div>
                <div v-if="parseResult(item)!.advice!.flirtyVariant" class="variant">
                  <span class="variant-tag">会撩版</span>{{ parseResult(item)!.advice!.flirtyVariant }}
                </div>
                <div v-if="parseResult(item)!.advice!.assertiveVariant" class="variant">
                  <span class="variant-tag">强势版</span>{{ parseResult(item)!.advice!.assertiveVariant }}
                </div>
              </section>

              <section v-if="parseResult(item)!.actions" class="sec">
                <h4>行动收束</h4>
                <p><b>现在做：</b>{{ parseResult(item)!.actions!.immediateAction }}</p>
                <p><b>观察窗口：</b>{{ parseResult(item)!.actions!.observationWindow }}</p>
                <p><b>停止条件：</b>{{ parseResult(item)!.actions!.stopCondition }}</p>
              </section>

              <div class="card-foot">
                <span>{{ item.model }}</span>
                <span>{{ item.promptTokens || 0 }} + {{ item.completionTokens || 0 }} tokens</span>
                <span>{{ item.durationMs || 0 }} ms</span>
              </div>
            </template>

            <el-alert v-else type="warning" :closable="false" title="结果无法解析，可删除后重新分析" />
          </div>
        </div>
      </div>
    </aside>

    <!-- ============ 说话人映射确认对话框 ============ -->
    <el-dialog v-model="identityDialog" title="确认「我」是哪个昵称" width="520px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="请按聊天记录里的原始标签选择。选错会让军师把双方认反，分析方向就全错了。"
        class="dlg-alert"
      />
      <el-form label-width="80px">
        <el-form-item label="我是">
          <el-select v-model="identityForm.selfLabels" multiple placeholder="选择代表你的标签" class="full-width">
            <el-option v-for="s in senderOptions" :key="s.label" :label="s.label + '（' + s.count + '条）'" :value="s.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="对方是">
          <el-select v-model="identityForm.targetLabels" multiple placeholder="选择代表对方的标签" class="full-width">
            <el-option v-for="s in senderOptions" :key="s.label" :label="s.label + '（' + s.count + '条）'" :value="s.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="identityDialog = false">取消</el-button>
        <el-button type="primary" :loading="identitySaving" @click="saveIdentity">保存并回写历史消息</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowRight, MagicStick, Refresh, Search, UserFilled } from '@element-plus/icons-vue'
import {
  analyzeWechatChat,
  confirmWechatIdentity,
  getWechatStatus,
  listWechatAnalysis,
  listWechatSessions,
  openWechatChat,
  syncWechatChat
} from '@/api/wechat/chat'
import type { WechatAnalysis, WechatAnalysisResult, WechatChatMessage, WechatSessionView } from '@/types'

/** 左上角搜索关键字 */
const keyword = ref('')
const sessionLoading = ref(false)
const messageLoading = ref(false)
const historyLoading = ref(false)
const syncing = ref(false)
const analyzing = ref(false)
const identitySaving = ref(false)
const identityDialog = ref(false)

const sessions = ref<WechatSessionView[]>([])
const activeSession = ref<WechatSessionView | null>(null)
const messages = ref<WechatChatMessage[]>([])
const analysisList = ref<WechatAnalysis[]>([])
const expandedId = ref<number | null>(null)
const goal = ref('')

const statusReady = ref(false)
const statusText = ref('正在检测数据源…')

const messageListRef = ref<HTMLElement | null>(null)

const identityForm = reactive<{ selfLabels: string[]; targetLabels: string[] }>({
  selfLabels: [],
  targetLabels: []
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

/** 当前会话的未分析条数 */
const unanalyzedCount = computed(() => activeSession.value?.unanalyzedCount ?? 0)

/** 自己那一侧的昵称，用于气泡头像 */
const selfLabel = computed(() => {
  const labels = activeSession.value?.selfLabels ?? []
  return labels.length ? labels[0] : '我'
})

/** 从消息里汇总出现过的原始发送者标签，供映射确认对话框选择 */
const senderOptions = computed<{ label: string; count: number }[]>(() => {
  const map = new Map<string, number>()
  messages.value.forEach((m: WechatChatMessage) => {
    const label = m.senderRaw || ''
    if (label) {
      map.set(label, (map.get(label) ?? 0) + 1)
    }
  })
  return Array.from(map.entries())
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count)
})

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
  } catch {
    statusReady.value = false
    statusText.value = '无法获取数据源状态'
  }
}

async function loadSessions() {
  sessionLoading.value = true
  try {
    const res = await listWechatSessions()
    sessions.value = res.data || []
  } finally {
    sessionLoading.value = false
  }
}

/**
 * 选择会话 —— 只读取聊天记录，不触发任何分析。
 */
async function selectSession(item: WechatSessionView) {
  activeSession.value = item
  messages.value = []
  analysisList.value = []
  expandedId.value = null
  dirtyCount.value = 0

  await Promise.all([loadMessages(item), item.sessionId ? loadHistory() : Promise.resolve()])
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
    messages.value = detail.messages || []
    // 同步后后端可能已经创建了会话或刷新了计数，用最新数据回填
    activeSession.value = detail.session
    dirtyCount.value = Math.max((detail.total || 0) - messages.value.length, 0)
    mergeSessionIntoList(detail.session)
    await scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || '读取聊天记录失败')
  } finally {
    messageLoading.value = false
  }
}

/** 手动同步记录（不分析） */
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

/**
 * 执行分析 —— 只把未分析过的记录发给 agent。
 */
async function runAnalysis() {
  const item = activeSession.value
  if (!item?.sessionId) {
    ElMessage.warning('请先同步该会话，归档后才能分析')
    return
  }
  if (unanalyzedCount.value === 0) {
    ElMessage.info('没有未分析过的记录')
    return
  }

  analyzing.value = true
  try {
    const res: any = await analyzeWechatChat({ sessionId: item.sessionId, goal: goal.value })
    const analysis: WechatAnalysis | undefined = res?.analysis
    if (analysis) {
      analysisList.value = [analysis, ...analysisList.value]
      expandedId.value = analysis.analysisId
    }
    const remaining = res?.remaining ?? 0
    ElMessage.success(
      res?.hasMore
        ? `分析完成，已消费未分析记录。还有 ${remaining} 条未分析，可再次点击继续`
        : '分析完成'
    )
    // 刷新会话计数与消息上的"已分析"标记
    await refreshSessionCounters()
    await loadMessages(activeSession.value!)
  } catch (e: any) {
    ElMessage.error(e?.message || '分析失败')
  } finally {
    analyzing.value = false
  }
}

/** 读取历史分析记录（不触发新分析） */
async function loadHistory() {
  const sessionId = activeSession.value?.sessionId
  if (!sessionId) {
    analysisList.value = []
    return
  }
  historyLoading.value = true
  try {
    const res = await listWechatAnalysis(sessionId, 20)
    analysisList.value = res.data || []
    if (analysisList.value.length) {
      expandedId.value = analysisList.value[0].analysisId
    }
  } catch {
    analysisList.value = []
  } finally {
    historyLoading.value = false
  }
}

/** 分析后重新拉会话列表，让计数与未分析徽标保持准确 */
async function refreshSessionCounters() {
  try {
    const res = await listWechatSessions()
    sessions.value = res.data || []
    const current = sessions.value.find((s: WechatSessionView) => s.chatUsername === activeUsername.value)
    if (current) {
      activeSession.value = current
    }
  } catch {
    /* 计数刷新失败不影响主流程 */
  }
}

async function saveIdentity() {
  const item = activeSession.value
  if (!item?.sessionId) {
    ElMessage.warning('请先同步该会话')
    return
  }
  if (identityForm.selfLabels.length === 0) {
    ElMessage.warning('请至少选择一个代表「我」的标签')
    return
  }
  identitySaving.value = true
  try {
    const res = await confirmWechatIdentity({
      sessionId: item.sessionId,
      selfUsername: item.chatUsername,
      selfLabels: identityForm.selfLabels,
      targetLabels: identityForm.targetLabels
    })
    if (res.data) {
      activeSession.value = res.data
      mergeSessionIntoList(res.data)
    }
    identityDialog.value = false
    ElMessage.success('映射已保存，历史消息的角色已回写')
    await loadMessages(activeSession.value!)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    identitySaving.value = false
  }
}

/** 打开映射对话框时，用当前会话已保存的映射或推定值预填 */
watch(identityDialog, (open: boolean) => {
  if (!open) {
    return
  }
  const item = activeSession.value
  identityForm.selfLabels = [...(item?.selfLabels ?? [])]
  identityForm.targetLabels = [...(item?.targetLabels ?? [])]
})

function mergeSessionIntoList(session: WechatSessionView) {
  const idx = sessions.value.findIndex((s: WechatSessionView) => s.chatUsername === session.chatUsername)
  if (idx >= 0) {
    sessions.value[idx] = session
  } else {
    sessions.value.unshift(session)
  }
}

function toggleAnalysis(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

/**
 * 解析后端存下来的结果 JSON。
 *
 * 解析失败返回 null，界面会给出"结果无法解析"的提示，而不是直接崩掉。
 */
const resultCache = new Map<number, WechatAnalysisResult | null>()
function parseResult(item: WechatAnalysis): WechatAnalysisResult | null {
  if (resultCache.has(item.analysisId)) {
    return resultCache.get(item.analysisId) ?? null
  }
  let parsed: WechatAnalysisResult | null = null
  if (item.resultJson) {
    try {
      parsed = JSON.parse(item.resultJson) as WechatAnalysisResult
    } catch {
      parsed = null
    }
  }
  resultCache.set(item.analysisId, parsed)
  return parsed
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
    media: msg.kind === 'MEDIA',
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

async function scrollToBottom() {
  await nextTick()
  const el = messageListRef.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}
</script>

<style scoped lang="scss">
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
  background: darken($wechat-side, 3%);
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
    background: darken($wechat-side, 5%);
  }

  &.active {
    background: darken($wechat-side, 10%);
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

.side-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-top: 1px solid $wechat-border;
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

.btn-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 5px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.3);
  font-size: 11px;
  line-height: 16px;
}

.identity-alert {
  margin: 8px 16px 0;
}

.identity-hint {
  font-size: 12px;
  line-height: 1.6;
  color: #8a6d3b;
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

.msg-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: 11px;
  color: #a5a5a5;
}

/* 未分析的消息在气泡下标注，让"分析会发送哪些内容"一目了然 */
.msg-dirty {
  color: #e6a23c;
}

.msg-clean {
  color: #b7b7b7;
}

/* ---------------- 右侧分析面板 ---------------- */
.analysis-panel {
  width: 400px;
  flex: 0 0 400px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: 1px solid $wechat-border;
}

.analysis-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid $wechat-border;
}

.panel-title {
  font-size: 15px;
  font-weight: 500;
}

.analysis-goal {
  padding: 12px 14px;
  border-bottom: 1px solid $wechat-border;
}

.goal-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
}

.goal-hint {
  font-size: 12px;
  color: #9a9a9a;
}

.analysis-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
}

.analysis-card {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 10px;
  overflow: hidden;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  background: #fafafa;
  cursor: pointer;

  &:hover {
    background: #f2f6fc;
  }
}

.card-head-left,
.card-head-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-count,
.card-time {
  font-size: 12px;
  color: #9a9a9a;
}

.card-goal {
  padding: 6px 10px;
  font-size: 12px;
  color: #7a7a7a;
  background: #fcfcfc;
  border-top: 1px solid #f5f5f5;
}

.card-detail {
  padding: 10px;
}

.crisis-box {
  padding: 10px;
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 4px;
  color: #b13a3a;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.sec {
  margin-bottom: 12px;

  h4 {
    margin: 0 0 6px;
    font-size: 13px;
    color: #303133;
    padding-left: 7px;
    border-left: 3px solid #409eff;
  }

  p {
    margin: 4px 0;
    font-size: 13px;
    line-height: 1.7;
    color: #4a4a4a;
  }

  ul {
    margin: 4px 0;
    padding-left: 20px;

    li {
      font-size: 13px;
      line-height: 1.7;
      color: #4a4a4a;
    }
  }
}

.primary-advice {
  padding: 8px 10px;
  background: #ecf5ff;
  border-radius: 4px;
  font-weight: 500;
  color: #1a5fb4 !important;
}

.primary-advice::before {
  content: '首选 ';
  color: #409eff;
  font-weight: 400;
}

.fact-group {
  margin-bottom: 6px;
}

.fact-title {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 3px;
  font-size: 11px;
  margin-bottom: 2px;

  &.known {
    background: #e8f5e9;
    color: #2e7d32;
  }

  &.infer {
    background: #fff8e1;
    color: #a67c00;
  }

  &.unknown {
    background: #f0f0f0;
    color: #6a6a6a;
  }
}

.variant {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.7;
  color: #4a4a4a;
}

.variant-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #f0f2f5;
  color: #6a6a6a;
  font-size: 11px;
}

.card-foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px dashed #ebeef5;
  font-size: 11px;
  color: #b0b0b0;
}

.dlg-alert {
  margin-bottom: 14px;
}

.full-width {
  width: 100%;
}
</style>
