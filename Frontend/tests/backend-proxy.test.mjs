import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

import { loadConfigFromFile } from 'vite'

const frontendRoot = resolve(import.meta.dirname, '..')
const configPath = resolve(frontendRoot, 'vite.config.ts')
const expectedBackendUrl = 'http://localhost:8080'

test('all Vite backend proxies use the single configured backend address', async () => {
  const configSource = readFileSync(configPath, 'utf8')
  const backendUrlMatches = configSource.match(/http:\/\/localhost:8080/g) ?? []

  assert.equal(backendUrlMatches.length, 1, 'backend address must have one source in vite.config.ts')

  const loaded = await loadConfigFromFile(
    {
      command: 'serve',
      mode: 'development',
      isSsrBuild: false,
      isPreview: false
    },
    configPath,
    frontendRoot
  )

  assert.ok(loaded)
  const proxy = loaded.config.server?.proxy
  assert.ok(proxy)
  assert.equal(proxy['/dev-api'].target, expectedBackendUrl)
  assert.equal(proxy['^/v3/api-docs/(.*)'].target, expectedBackendUrl)
})
