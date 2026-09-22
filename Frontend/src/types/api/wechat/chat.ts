import type { BaseEntity } from '../common'

/** 数据源状态 */
export interface WechatStatus {
  /** 数据源是否就绪 */
  ready: boolean
  /**
   * 数据源标识。
   *
   * `text` = 粘贴文本（默认）；`mock` = 内置样例；`cli` = 本地 wechat-cli。
   * 只有 `text` 支持「导入文本」。
   */
  mode: string
  /** 人类可读说明，未就绪时含修复建议 */
  message: string
  /**
   * 调用 agent 的能力是否已启用。
   *
   * 后端已移除调用 agent 的代码，当前固定为 false；
   * 前端据此把分析入口置为不可用，避免用户反复点击。
   */
  agentEnabled?: boolean
}

/** 会话列表项 */
export interface WechatSessionView {
  /** 会话ID；未归档的会话为 null，需先打开会话才会创建 */
  sessionId: number | null
  /** 微信内部标识 */
  chatUsername: string
  /** 展示名，即左侧列表按它分类的"昵称" */
  displayName: string
  /** 是否群聊 */
  group: boolean
  /** 说话人映射是否已确认 */
  identityConfirmed: boolean
  /** 「我」的标签 */
  selfLabels: string[]
  /** 对方标签 */
  targetLabels: string[]
  /** 已归档消息数 */
  msgCount: number
  /** 未分析消息数 */
  unanalyzedCount: number
  /** 历史分析次数 */
  analysisCount: number
  /** 最后一条消息时间 */
  lastTime?: string | null
  /** 最后一条消息预览 */
  lastMessage?: string | null
  /** 最后同步时间 */
  lastSyncTime?: string | null
  /** 当前好感度百分比，null 表示尚未计算 */
  affinityScore?: number | null
  /** 用户填写的认识日期 */
  acquaintanceDate?: string | null
  /** 认识天数，认识当天为第 1 天 */
  acquaintanceDays?: number | null
  /** 我的回答综合发挥得分 */
  myPerformanceScore?: number | null
  /** 下一步建议，后续由大模型结果填充 */
  nextStepSuggestion?: string | null
  /** 是否已归档 */
  archived: boolean
}

/** 聊天界面中的一条消息 */
export interface WechatChatMessage {
  messageId: number
  time: string
  date: string
  /** SELF / TARGET / OTHER / UNKNOWN */
  senderRole: string
  /** 原始发送者标签 */
  senderRaw: string
  content: string
  /**
   * TEXT / QUOTE / MEDIA / VOICE / STICKER / CALL / OTHER。
   *
   * VOICE（[语音]）与 STICKER（[动画表情]）只在聊天记录里展示，不参与分析。
   */
  kind: string
  /** 是否已参与过分析 */
  analyzed: boolean
  /** 对方消息情绪标签 */
  emotionTag?: string | null
  /** 情绪标签百分比 */
  emotionPercent?: number | null
  /** 对方消息意图标签 */
  intentTag?: string | null
  /** 意图标签百分比 */
  intentPercent?: number | null
  /** 我方回答评级 */
  replyRating?: string | null
  /** 我方回答得分 */
  replyScore?: number | null
}

/** 文本导入请求：格式为每 3 行一条消息 —— 昵称 / 时间 / 消息内容 */
export interface WechatTextImportRequest {
  /** 会话名，可留空：留空时由后端按文本内容自动识别 */
  chatName?: string
  /** 粘贴的聊天记录原文 */
  text: string
}

/** 设置会话认识日期；日期为空表示清除 */
export interface WechatAcquaintanceDateRequest {
  sessionId: number
  acquaintanceDate?: string | null
}

/** 打开会话的返回 */
export interface WechatChatDetail {
  session: WechatSessionView
  messages: WechatChatMessage[]
  total: number
  offset: number
  limit: number
}

/** 军师分析结果 —— 与后端 AnalysisResult 结构一一对应 */
export interface WechatAnalysisResult {
  emotionLanding: string | null
  facts: {
    knownFacts: string[]
    inferences: string[]
    keyUnknowns: string[]
  } | null
  interest: { evaluation: string | null } | null
  advice: {
    primary: string | null
    reasons: string[]
    steadyVariant: string | null
    flirtyVariant: string | null
    assertiveVariant: string | null
  } | null
  actions: {
    immediateAction: string | null
    observationWindow: string | null
    stopCondition: string | null
    followUpQuestions: string[]
  } | null
  crisisReferral: boolean
}

/** 分析记录 */
export interface WechatAnalysis extends BaseEntity {
  analysisId: number
  sessionId: number
  chatName: string
  goal: string
  startTime?: string
  endTime?: string
  messageCount: number
  identityDesc?: string
  refsUsed?: string
  /** 结构化结果的 JSON 字符串 */
  resultJson?: string | null
  promptSnapshot?: string | null
  /** 0 否 1 是 */
  crisis: string
  crisisMessage?: string | null
  model?: string
  promptTokens?: number
  completionTokens?: number
  durationMs?: number
  /** 0 成功 1 失败 */
  status: string
  errorMsg?: string
}

/** 身份映射候选 */
export interface WechatIdentityCandidate {
  chatName: string
  group: boolean
  selfUsername?: string | null
  autoDetected: boolean
  senders: { label: string; count: number }[]
  guessSelf: string[]
  guessTarget: string[]
  hint: string
}
