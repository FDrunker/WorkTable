import request from '@/utils/request'
import type {
  AjaxResult,
  PublicCustomerServiceBootstrap,
  PublicCustomerServiceConversation,
  PublicCustomerServiceConversationDetail,
  PublicCustomerServiceReply
} from '@/types'

const anonymousRequest = { isToken: false, repeatSubmit: false }

export function getCustomerServiceAiBootstrap(
  visitorId: string
): Promise<AjaxResult<PublicCustomerServiceBootstrap>> {
  return request({
    url: '/public/customer-service-ai/bootstrap',
    method: 'get',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function createCustomerServiceAiConversation(
  visitorId: string
): Promise<AjaxResult<PublicCustomerServiceConversation>> {
  return request({
    url: '/public/customer-service-ai/conversations',
    method: 'post',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function getCustomerServiceAiConversation(
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<PublicCustomerServiceConversationDetail>> {
  return request({
    url: `/public/customer-service-ai/conversations/${encodeURIComponent(conversationId)}`,
    method: 'get',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function deleteCustomerServiceAiConversation(
  conversationId: string,
  visitorId: string
): Promise<AjaxResult<void>> {
  return request({
    url: `/public/customer-service-ai/conversations/${encodeURIComponent(conversationId)}`,
    method: 'delete',
    params: { visitorId },
    ...anonymousRequest
  })
}

export function sendCustomerServiceAiMessage(
  conversationId: string,
  visitorId: string,
  issueType: string,
  question: string
): Promise<AjaxResult<PublicCustomerServiceReply>> {
  return request({
    url: `/public/customer-service-ai/conversations/${encodeURIComponent(conversationId)}/messages`,
    method: 'post',
    data: { visitorId, issueType, question },
    timeout: 180000,
    ...anonymousRequest
  })
}
