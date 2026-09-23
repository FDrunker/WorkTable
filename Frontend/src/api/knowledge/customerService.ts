import request from '@/utils/request'
import type {
  AjaxResult,
  CustomerServiceChatRequest,
  CustomerServiceChatResponse,
  CustomerServiceConfig,
  CustomerServiceOptions
} from '@/types'

export function getCustomerServiceConfig(): Promise<AjaxResult<CustomerServiceConfig>> {
  return request({
    url: '/knowledge/customer-service/config',
    method: 'get'
  })
}

export function getCustomerServiceOptions(): Promise<AjaxResult<CustomerServiceOptions>> {
  return request({
    url: '/knowledge/customer-service/options',
    method: 'get'
  })
}

export function updateCustomerServiceConfig(
  data: CustomerServiceConfig
): Promise<AjaxResult<CustomerServiceConfig>> {
  return request({
    url: '/knowledge/customer-service/config',
    method: 'put',
    data
  })
}

export function chatWithCustomerService(
  data: CustomerServiceChatRequest
): Promise<AjaxResult<CustomerServiceChatResponse>> {
  return request({
    url: '/knowledge/customer-service/chat',
    method: 'post',
    data,
    timeout: 180000
  })
}
