import request from '@/utils/request'
import type {
  AjaxResult,
  WechatAcquaintanceDateRequest,
  WechatChatDetail,
  WechatSessionView,
  WechatStatus,
  WechatTextImportCommitRequest,
  WechatTextImportPreview,
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
 * 注意：这个接口只读归档记录，必要时做一次幂等同步。
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

/** 解析粘贴的聊天记录，返回昵称候选，不写入数据库 */
export function previewWechatText(
  data: WechatTextImportRequest
): Promise<AjaxResult<WechatTextImportPreview>> {
  return request({
    url: '/wechat/text/preview',
    method: 'post',
    data
  })
}

/** 确认自己的昵称并归档到对方昵称对应的会话 */
export function commitWechatText(
  data: WechatTextImportCommitRequest
): Promise<AjaxResult<WechatChatDetail>> {
  return request({
    url: '/wechat/text/commit',
    method: 'post',
    data
  })
}

/** 删除会话及其消息 */
export function deleteWechatSession(sessionId: number): Promise<AjaxResult> {
  return request({
    url: '/wechat/session/' + sessionId,
    method: 'delete'
  })
}

/** 设置或清除会话认识日期 */
export function updateWechatAcquaintanceDate(
  data: WechatAcquaintanceDateRequest
): Promise<AjaxResult<WechatSessionView>> {
  return request({
    url: '/wechat/session/acquaintance-date',
    method: 'post',
    data
  })
}

/** 手动同步聊天记录 */
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
