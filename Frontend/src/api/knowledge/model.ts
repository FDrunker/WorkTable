import request from '@/utils/request'
import type {
  AiModelConfig,
  AiModelProvider,
  AiModelQueryParams,
  AjaxResult,
  TableDataInfo
} from '@/types'

export function listAiModels(query: AiModelQueryParams): Promise<TableDataInfo<AiModelConfig[]>> {
  return request({
    url: '/knowledge/model/list',
    method: 'get',
    params: query
  })
}

export function getAiModel(modelId: number): Promise<AjaxResult<AiModelConfig>> {
  return request({
    url: '/knowledge/model/' + modelId,
    method: 'get'
  })
}

export function listAiModelOptions(modelType?: string): Promise<AjaxResult<AiModelConfig[]>> {
  return request({
    url: '/knowledge/model/options',
    method: 'get',
    params: { modelType }
  })
}

export function listAiModelProviders(): Promise<AjaxResult<AiModelProvider[]>> {
  return request({
    url: '/knowledge/model/providers',
    method: 'get'
  })
}

export function addAiModel(data: AiModelConfig): Promise<AjaxResult<AiModelConfig>> {
  return request({
    url: '/knowledge/model',
    method: 'post',
    data
  })
}

export function updateAiModel(data: AiModelConfig): Promise<AjaxResult<AiModelConfig>> {
  return request({
    url: '/knowledge/model',
    method: 'put',
    data
  })
}

export function deleteAiModels(modelIds: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/knowledge/model/' + modelIds,
    method: 'delete'
  })
}

export function testAiModel(modelId: number): Promise<AjaxResult<AiModelConfig>> {
  return request({
    url: '/knowledge/model/' + modelId + '/test',
    method: 'post',
    timeout: 60000
  })
}
