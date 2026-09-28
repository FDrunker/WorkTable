import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const root = resolve(import.meta.dirname, '..', '..')

function read(relativePath) {
  const absolutePath = resolve(root, relativePath)
  assert.equal(existsSync(absolutePath), true, `missing file: ${relativePath}`)
  return readFileSync(absolutePath, 'utf8')
}

test('knowledge documents no longer expose a user-entered separator', () => {
  const page = read('Frontend/src/views/knowledge/collection/index.vue')
  assert.doesNotMatch(page, /label="分隔符"|prop="separator"|documentForm\.separator/)

  const types = read('Frontend/src/types/api/knowledge/collection.ts')
  assert.doesNotMatch(types, /^\s*separator:\s*string$/m)
  assert.doesNotMatch(types, /^\s*chunkSeparator:\s*string$/m)
})
