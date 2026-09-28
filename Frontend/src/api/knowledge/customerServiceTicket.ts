import request from '@/utils/request'
import type {
  AjaxResult,
  CustomerServiceTicket,
  CustomerServiceTicketHandleRequest,
  CustomerServiceTicketQuery
} from '@/types'

export function listCustomerServiceTickets(
  params: CustomerServiceTicketQuery = {}
): Promise<AjaxResult<CustomerServiceTicket[]>> {
  return request({
    url: '/knowledge/customer-service/tickets',
    method: 'get',
    params
  })
}

export function handleCustomerServiceTicket(
  ticketId: number,
  data: CustomerServiceTicketHandleRequest
): Promise<AjaxResult<CustomerServiceTicket>> {
  return request({
    url: `/knowledge/customer-service/tickets/${ticketId}/handle`,
    method: 'post',
    data
  })
}
