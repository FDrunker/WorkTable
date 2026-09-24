import request from '@/utils/request'
import type {
  AjaxResult,
  PublicChatBootstrap,
  PublicChatConversation,
  PublicChatConversationDetail,
  PublicChatReply,
  PublicChatScene
} from '@/types'

const publicHeaders = { isToken: false, repeatSubmit: false }

export function listPublicChatScenes(): Promise<AjaxResult<PublicChatScene[]>> {
  return request({
    url: '/public/ai-chat/scenes',
    method: 'get',
    headers: publicHeaders
  })
}

export function getPublicChatBootstrap(
  scene: string,
  visitorId: string
): Promise<AjaxResult<PublicChatBootstrap>> {
  return request({
    url: `/public/ai-chat/${encodeURIComponent(scene)}/bootstrap`,
    method: 'get',
    params: { visitorId },
    headers: publicHeaders
  })
}

export function createPublicChatConversation(
  scene: string,
  visitorId: string
): Promise<AjaxResult<PublicChatConversation>> {
  return request({
    url: `/public/ai-chat/${encodeURIComponent(scene)}/conversations`,
    method: 'post',
    params: { visitorId },
    headers: publicHeaders
  })
}

export function getPublicChatConversation(
  scene: string,
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<PublicChatConversationDetail>> {
  return request({
    url: `/public/ai-chat/${encodeURIComponent(scene)}/conversations/${encodeURIComponent(conversationId)}`,
    method: 'get',
    params: { visitorId },
    headers: publicHeaders
  })
}

export function deletePublicChatConversation(
  scene: string,
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/public/ai-chat/${encodeURIComponent(scene)}/conversations/${encodeURIComponent(conversationId)}`,
    method: 'delete',
    params: { visitorId },
    headers: publicHeaders
  })
}

export function sendPublicChatMessage(
  scene: string,
  conversationId: string,
  visitorId: string,
  question: string
): Promise<AjaxResult<PublicChatReply>> {
  return request({
    url: `/public/ai-chat/${encodeURIComponent(scene)}/conversations/${encodeURIComponent(conversationId)}/messages`,
    method: 'post',
    data: { visitorId, question },
    timeout: 180000,
    headers: publicHeaders
  })
}
