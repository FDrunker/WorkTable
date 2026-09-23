import type { PageDomain } from '../common'

export interface ChromaCollection {
  id: string
  name: string
  documentCount: number
  metadata?: Record<string, any>
}

export interface CollectionCreateRequest {
  name: string
}

export interface KnowledgeDocument {
  documentId: string
  collectionId: string
  collectionName?: string
  documentName: string
  sourceType: string
  chunkCount: number
  embeddingModelId?: number
  status: string
  managed: boolean
  createTime?: string
}

export interface KnowledgeDocumentDetail {
  documentId: string
  collectionId: string
  collectionName?: string
  documentName: string
  sourceType: string
  content: string
  chunkSeparator: string
  chunkSize: number
  chunkOverlap: number
  embeddingModelId?: number
  status: string
  chunkCount: number
}

export interface KnowledgeDocumentQueryParams extends PageDomain {
  collectionId?: string
  collectionName?: string
  documentName?: string
}

export interface KnowledgeDocumentChunk {
  id: string
  content: string
  metadata: Record<string, any>
}

export interface DocumentImportRequest {
  documentId?: string
  collectionId: string
  collectionName?: string
  documentName: string
  modelId?: number
  separator: string
  chunkSize: number
  chunkOverlap: number
  content?: string
}

export interface KnowledgeTask {
  taskId: number
  taskType: string
  documentId?: string
  documentName?: string
  collectionId?: string
  collectionName?: string
  modelId?: number
  status: string
  progress: number
  stage: string
  message?: string
  errorMessage?: string
  retryCount?: number
  startTime?: string
  endTime?: string
  createTime?: string
}

export interface KnowledgeTaskQueryParams extends PageDomain {
  collectionId?: string
  documentId?: string
  status?: string
  taskType?: string
}
