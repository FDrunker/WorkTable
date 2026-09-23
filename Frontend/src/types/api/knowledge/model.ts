import type { BaseEntity, PageDomain } from '../common'

export interface AiModelConfig extends BaseEntity {
  modelId?: number
  modelName?: string
  provider?: string
  modelType?: string
  modelCode?: string
  baseUrl?: string
  apiKey?: string
  apiKeyMasked?: string
  enabled?: string
  isDefault?: string
  extraConfig?: string
  lastTestStatus?: string
  lastTestMessage?: string
  lastTestTime?: string
}

export interface AiModelQueryParams extends PageDomain {
  modelName?: string
  provider?: string
  modelType?: string
  enabled?: string
}

export interface AiModelProvider {
  code: string
  name: string
  defaultBaseUrl: string
  modelTypes: string[]
}
