import request from '@/utils/request'
import type {
  AjaxResult,
  CustomerServiceChatRequest,
  CustomerServiceChatResponse,
  CustomerServiceConfig,
  CustomerServiceOptions,
  CustomerServiceScene
} from '@/types'

export function getCustomerServiceScenes(): Promise<AjaxResult<CustomerServiceScene[]>> {
  return request({
    url: '/knowledge/ai-chat/scenes',
    method: 'get'
  })
}

export function getCustomerServiceConfig(scene = 'admin'): Promise<AjaxResult<CustomerServiceConfig>> {
  return request({
    url: '/knowledge/ai-chat/config',
    method: 'get',
    params: { scene }
  })
}

export function getCustomerServiceOptions(): Promise<AjaxResult<CustomerServiceOptions>> {
  return request({
    url: '/knowledge/ai-chat/options',
    method: 'get'
  })
}

export function updateCustomerServiceConfig(
  data: CustomerServiceConfig
): Promise<AjaxResult<CustomerServiceConfig>> {
  return request({
    url: '/knowledge/ai-chat/config',
    method: 'put',
    data
  })
}

export function chatWithCustomerService(
  data: CustomerServiceChatRequest,
  scene = 'admin'
): Promise<AjaxResult<CustomerServiceChatResponse>> {
  return request({
    url: '/knowledge/ai-chat/chat',
    method: 'post',
    data,
    params: { scene },
    timeout: 180000
  })
}
