export interface RequestFlags {
  skipToken: boolean
  skipRepeatSubmit: boolean
}

export function getRequestFlags(config: {
  url?: string
  isToken?: boolean
  repeatSubmit?: boolean
  headers?: Record<string, any>
}): RequestFlags {
  const headers = config.headers || {}
  const publicRequest = String(config.url || '').startsWith('/public/')
  return {
    skipToken: publicRequest || config.isToken === false || headers.isToken === false,
    skipRepeatSubmit: publicRequest || config.repeatSubmit === false || headers.repeatSubmit === false
  }
}
