export function normalizeFormDataRequest(config: any) {
  if (typeof FormData === 'undefined' || !(config.data instanceof FormData)) {
    return config
  }

  if (config.headers && typeof config.headers.setContentType === 'function') {
    config.headers.setContentType(undefined)
  }

  return config
}
