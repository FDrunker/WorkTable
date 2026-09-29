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

test('knowledge documents use markdown structure chunking without a separator', () => {
  const page = read('Frontend/src/views/knowledge/collection/index.vue')
  assert.doesNotMatch(page, /label="分隔符"|prop="separator"|documentForm\.separator/)
  assert.match(page, /accept="\.md,text\/markdown"/)
  assert.match(page, /粘贴 Markdown/)

  const types = read('Frontend/src/types/api/knowledge/collection.ts')
  assert.doesNotMatch(types, /^\s*separator:\s*string$/m)
  assert.doesNotMatch(types, /^\s*chunkSeparator:\s*string$/m)

  const service = read(
    'Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/service/KnowledgeDocumentService.java'
  )
  assert.match(service, /endsWith\("\.md"\)/)
  assert.match(service, /request\.setContent\(null\)/)

  const mapper = read(
    'Backend/ruoyi-knowledge/src/main/resources/mapper/knowledge/KnowledgeDocumentMapper.xml'
  )
  assert.doesNotMatch(mapper, /(?:select|insert into|update)[\s\S]*?\bcontent\b/)
})
