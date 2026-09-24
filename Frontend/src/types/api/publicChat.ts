import type { CustomerServiceSource } from './knowledge/customerService'

export interface PublicChatScene {
  scene: string
  title: string
  welcomeMessage: string
}

export interface PublicChatConversation {
  conversationId: string
  title: string
  createTime?: string
  updateTime?: string
  messageCount: number
  lastMessage?: string
}

export interface PublicChatMessage {
  messageId?: number
  role: 'user' | 'assistant'
  content: string
  intent?: string
  intentConfidence?: number
  needsClarification?: boolean
  sources?: CustomerServiceSource[]
  createTime?: string
}

export interface PublicChatBootstrap {
  scene: PublicChatScene
  configured: boolean
  enabled: boolean
  conversations: PublicChatConversation[]
}

export interface PublicChatConversationDetail {
  conversationId: string
  scene: string
  title: string
  createTime?: string
  updateTime?: string
  messages: PublicChatMessage[]
}

export interface PublicChatReply {
  conversationId: string
  title: string
  userMessage: PublicChatMessage
  assistantMessage: PublicChatMessage
}
