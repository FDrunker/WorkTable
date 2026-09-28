import request from '@/utils/request'
import type {
  AjaxResult,
  ResumeBootstrap,
  ResumeConversation,
  ResumeConversationDetail,
  ResumeReply
} from '@/types'

const anonymousRequest = { isToken: false, repeatSubmit: false }

export function getResumeBootstrap(visitorId: string): Promise<AjaxResult<ResumeBootstrap>> {
  return request({
    url: '/public/resume-ai/bootstrap',
    method: 'get',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function createResumeConversation(
  visitorId: string
): Promise<AjaxResult<ResumeConversation>> {
  return request({
    url: '/public/resume-ai/conversations',
    method: 'post',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function getResumeConversation(
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<ResumeConversationDetail>> {
  return request({
    url: `/public/resume-ai/conversations/${encodeURIComponent(conversationId)}`,
    method: 'get',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function deleteResumeConversation(
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/public/resume-ai/conversations/${encodeURIComponent(conversationId)}`,
    method: 'delete',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function sendResumeMessage(
  conversationId: string,
  visitorId: string,
  question: string
): Promise<AjaxResult<ResumeReply>> {
  return request({
    url: `/public/resume-ai/conversations/${encodeURIComponent(conversationId)}/messages`,
    method: 'post',
    data: { visitorId, question },
    timeout: 180000,
    ...anonymousRequest
  })
}
