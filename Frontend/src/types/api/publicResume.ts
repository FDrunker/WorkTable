export interface ResumeScene {
  scene: string
  title: string
  welcomeMessage: string
}

export interface ResumeConversation {
  conversationId: string
  title: string
  createTime?: string
  updateTime?: string
  messageCount: number
  lastMessage?: string
}

export interface ResumeSource {
  id: string
  collectionId: string
  collectionName?: string
  documentId?: string
  documentName?: string
  chunkIndex?: number
  content: string
  distance?: number
}

export interface ResumeMessage {
  messageId?: number
  role: 'user' | 'assistant'
  content: string
  needsClarification?: boolean
  sources?: ResumeSource[]
  createTime?: string
}

export interface ResumeBootstrap {
  scene: ResumeScene
  configured: boolean
  enabled: boolean
  conversations: ResumeConversation[]
}

export interface ResumeConversationDetail {
  conversationId: string
  scene: string
  title: string
  createTime?: string
  updateTime?: string
  messages: ResumeMessage[]
}

export interface ResumeReply {
  conversationId: string
  title: string
  userMessage: ResumeMessage
  assistantMessage: ResumeMessage
}
