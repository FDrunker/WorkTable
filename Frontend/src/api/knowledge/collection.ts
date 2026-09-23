import request from '@/utils/request'
import type {
  AjaxResult,
  ChromaCollection,
  CollectionCreateRequest,
  DocumentImportRequest,
  KnowledgeDocument,
  KnowledgeDocumentChunk,
  KnowledgeDocumentDetail,
  KnowledgeDocumentQueryParams,
  KnowledgeTask,
  KnowledgeTaskQueryParams,
  TableDataInfo
} from '@/types'

export function listCollections(name?: string): Promise<AjaxResult<ChromaCollection[]>> {
  return request({
    url: '/knowledge/collection/list',
    method: 'get',
    params: { name }
  })
}

export function createCollection(data: CollectionCreateRequest): Promise<AjaxResult<ChromaCollection>> {
  return request({
    url: '/knowledge/collection',
    method: 'post',
    data
  })
}

export function deleteCollections(collectionIds: string[]): Promise<AjaxResult> {
  return request({
    url: '/knowledge/collection',
    method: 'delete',
    data: { collectionIds }
  })
}

export function listKnowledgeDocuments(
  query: KnowledgeDocumentQueryParams
): Promise<TableDataInfo<KnowledgeDocument[]>> {
  return request({
    url: '/knowledge/document/list',
    method: 'get',
    params: query
  })
}

export function getKnowledgeDocument(documentId: string): Promise<AjaxResult<KnowledgeDocumentDetail>> {
  return request({
    url: '/knowledge/document/' + encodeURIComponent(documentId),
    method: 'get'
  })
}

export function listKnowledgeChunks(
  collectionId: string,
  documentId: string
): Promise<AjaxResult<KnowledgeDocumentChunk[]>> {
  return request({
    url: '/knowledge/document/' + encodeURIComponent(documentId) + '/chunks',
    method: 'get',
    params: { collectionId }
  })
}

export function importKnowledgeDocument(
  data: DocumentImportRequest,
  file?: File
): Promise<AjaxResult<KnowledgeTask>> {
  const formData = new FormData()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      formData.append(key, String(value))
    }
  })
  if (file) {
    formData.append('file', file)
  }
  return request({
    url: '/knowledge/document/import',
    method: 'post',
    data: formData,
    timeout: 60000
  })
}

export function updateKnowledgeDocument(
  documentId: string,
  data: DocumentImportRequest,
  file?: File
): Promise<AjaxResult<KnowledgeTask>> {
  const formData = new FormData()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      formData.append(key, String(value))
    }
  })
  if (file) {
    formData.append('file', file)
  }
  return request({
    url: '/knowledge/document/' + encodeURIComponent(documentId),
    method: 'put',
    data: formData,
    timeout: 60000
  })
}

export function deleteKnowledgeDocument(collectionId: string, documentId: string): Promise<AjaxResult> {
  return request({
    url: '/knowledge/document/' + encodeURIComponent(documentId),
    method: 'delete',
    params: { collectionId }
  })
}

export function reindexKnowledgeDocument(
  collectionId: string,
  documentId: string,
  modelId?: number
): Promise<AjaxResult<KnowledgeTask>> {
  return request({
    url: '/knowledge/document/' + encodeURIComponent(documentId) + '/reindex',
    method: 'post',
    data: {
      collectionId,
      modelId
    }
  })
}

export function listKnowledgeTasks(query: KnowledgeTaskQueryParams): Promise<TableDataInfo<KnowledgeTask[]>> {
  return request({
    url: '/knowledge/task/list',
    method: 'get',
    params: query
  })
}

export function cancelKnowledgeTask(taskId: number): Promise<AjaxResult> {
  return request({
    url: '/knowledge/task/' + taskId + '/cancel',
    method: 'post'
  })
}

export function retryKnowledgeTask(taskId: number): Promise<AjaxResult<KnowledgeTask>> {
  return request({
    url: '/knowledge/task/' + taskId + '/retry',
    method: 'post'
  })
}
