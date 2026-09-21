import request from '@/utils/request'
import type {
  AjaxResult,
  WechatAnalysis,
  WechatChatDetail,
  WechatSessionView,
  WechatStatus,
  WechatTextImportRequest
} from '@/types'

/** 查询数据源与知识库状态 */
export function getWechatStatus(): Promise<AjaxResult<WechatStatus>> {
  return request({
    url: '/wechat/status',
    method: 'get'
  })
}

/** 查询会话列表（已归档 + 数据源发现） */
export function listWechatSessions(discoverLimit = 50): Promise<AjaxResult<WechatSessionView[]>> {
  return request({
    url: '/wechat/sessions',
    method: 'get',
    params: { discoverLimit }
  })
}

/**
 * 打开会话，读取聊天记录。
 *
 * 注意：这个接口只读归档记录（必要时做一次幂等同步），不会调用 LLM。
 */
export function openWechatChat(params: {
  sessionId?: number | null
  chatUsername?: string
  displayName?: string
  group?: boolean
  offset?: number
  limit?: number
  sync?: boolean
}): Promise<AjaxResult<WechatChatDetail>> {
  return request({
    url: '/wechat/chat/open',
    method: 'get',
    params
  })
}

/**
 * 导入粘贴的聊天记录文本。
 *
 * 文本格式为每 3 行一条消息：昵称 / 时间 / 消息内容。
 * 返回归档后的聊天记录，前端可直接渲染，不必再请求一次。
 */
export function importWechatText(data: WechatTextImportRequest): Promise<AjaxResult<WechatChatDetail>> {
  return request({
    url: '/wechat/text/import',
    method: 'post',
    data
  })
}

/** 手动同步聊天记录（不分析） */
export function syncWechatChat(params: {
  sessionId?: number | null
  chatUsername?: string
  displayName?: string
  group?: boolean
}): Promise<AjaxResult<{ session: WechatSessionView; msgCount: number }>> {
  return request({
    url: '/wechat/chat/sync',
    method: 'post',
    params
  })
}

/** 确认说话人映射 */
export function confirmWechatIdentity(data: {
  sessionId: number
  selfUsername?: string | null
  selfLabels: string[]
  targetLabels: string[]
}): Promise<AjaxResult<WechatSessionView>> {
  return request({
    url: '/wechat/identity/confirm',
    method: 'post',
    data
  })
}

/** 查询历史分析记录（不触发新分析） */
export function listWechatAnalysis(sessionId: number, limit = 20): Promise<AjaxResult<WechatAnalysis[]>> {
  return request({
    url: '/wechat/analysis/list',
    method: 'get',
    params: { sessionId, limit }
  })
}

/** 查询分析详情（含 prompt 快照） */
export function getWechatAnalysis(analysisId: number): Promise<AjaxResult<WechatAnalysis>> {
  return request({
    url: '/wechat/analysis/' + analysisId,
    method: 'get'
  })
}

/** 删除分析记录 */
export function delWechatAnalysis(analysisIds: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/wechat/analysis/' + analysisIds,
    method: 'delete'
  })
}
