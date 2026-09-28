export type CustomerServiceTicketStatus = 'WAITING' | 'APPROVED' | 'MODIFIED' | 'ESCALATED'
export type CustomerServiceTicketPriority = 'low' | 'medium' | 'high'
export type CustomerServiceTicketAction = 'approve' | 'modify' | 'escalate'

export interface CustomerServiceTicket {
  ticketId: number
  ticketNo: string
  conversationId: string
  visitorId: string
  issueType: string
  question: string
  category: 'standard' | 'complex'
  classificationReason: string
  priority: CustomerServiceTicketPriority
  status: CustomerServiceTicketStatus
  preanalysisSummary?: string
  preanalysisSolution?: string
  suggestedReply?: string
  handlerReply?: string
  handleNote?: string
  handledBy?: string
  handleTime?: string
  createTime?: string
  updateTime?: string
}

export interface CustomerServiceTicketQuery {
  ticketNo?: string
  status?: string
  priority?: string
  issueType?: string
}

export interface CustomerServiceTicketHandleRequest {
  action: CustomerServiceTicketAction
  priority: CustomerServiceTicketPriority
  replyContent?: string
  note?: string
}
