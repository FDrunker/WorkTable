import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import test from 'node:test'

const root = resolve(import.meta.dirname, '..')
const publicChatPages = [
  'src/views/public/customer-service/index.vue',
  'src/views/public/resume/index.vue'
]

test('public chat pages keep the composer inside the visible viewport', () => {
  for (const relativePath of publicChatPages) {
    const page = readFileSync(resolve(root, relativePath), 'utf8')

    assert.match(
      page,
      /\.public-body\s*\{[^}]*grid-template-rows:\s*minmax\(0,\s*1fr\);[^}]*\}/s,
      `${relativePath} must allow the chat row to shrink`
    )
    assert.match(
      page,
      /\.chat-main\s*\{[^}]*min-height:\s*0;[^}]*height:\s*100%;[^}]*\}/s,
      `${relativePath} must keep the chat column within the available height`
    )
    assert.match(page, /height:\s*calc\(100dvh - 64px\);/)
    assert.match(page, /height:\s*calc\(100dvh - 60px\);/)
  }
})
