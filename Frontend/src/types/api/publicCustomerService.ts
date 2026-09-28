export interface PublicCustomerServiceScene {
  scene: string
  title: string
  welcomeMessage: string
}

export interface PublicCustomerServiceConversation {
  conversationId: string
  title: string
  createTime?: string
  updateTime?: string
  messageCount: number
  lastMessage?: string
}

export interface PublicCustomerServiceSource {
  id: string
  collectionId: string
  collectionName?: string
  documentId?: string
  documentName?: string
  chunkIndex?: number
  content: string
  distance?: number
}

export interface PublicCustomerServiceMessage {
  messageId?: number
  role: 'user' | 'assistant'
  content: string
  needsClarification?: boolean
  sources?: PublicCustomerServiceSource[]
  category?: 'standard' | 'complex'
  priority?: 'low' | 'medium' | 'high'
  ticketNo?: string
  createTime?: string
}

export interface PublicCustomerServiceBootstrap {
  scene: PublicCustomerServiceScene
  configured: boolean
  enabled: boolean
  conversations: PublicCustomerServiceConversation[]
}

export interface PublicCustomerServiceConversationDetail {
  conversationId: string
  scene: string
  title: string
  createTime?: string
  updateTime?: string
  messages: PublicCustomerServiceMessage[]
}

export interface PublicCustomerServiceReply {
  conversationId: string
  title: string
  userMessage: PublicCustomerServiceMessage
  assistantMessage: PublicCustomerServiceMessage
  category: 'standard' | 'complex'
  priority: 'low' | 'medium' | 'high'
  ticketNo?: string
  ticketStatus: string
}
