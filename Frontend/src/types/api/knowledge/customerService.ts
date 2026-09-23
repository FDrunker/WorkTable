import type { AiModelConfig } from './model'
import type { ChromaCollection } from './collection'

export interface CustomerServiceConfig {
  configId?: number
  chatModelId?: number
  embeddingModelId?: number
  collectionIds: string[]
  topK: number
  enabled: string
  updateTime?: string
}

export interface CustomerServiceOptions {
  chatModels: AiModelConfig[]
  embeddingModels: AiModelConfig[]
  collections: ChromaCollection[]
}

export interface CustomerServiceChatTurn {
  role: 'user' | 'assistant'
  content: string
}

export interface CustomerServiceChatRequest {
  question: string
  history: CustomerServiceChatTurn[]
}

export interface CustomerServiceSource {
  id: string
  collectionId: string
  collectionName?: string
  documentId?: string
  documentName?: string
  chunkIndex?: number
  content: string
  distance?: number
}

export interface CustomerServiceChatResponse {
  answer: string
  intent: string
  intentConfidence?: number
  needsClarification: boolean
  sources: CustomerServiceSource[]
}
