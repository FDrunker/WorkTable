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

test('public chat pages expose a mobile scene switch', () => {
  const resumePage = readFileSync(resolve(root, publicChatPages[1]), 'utf8')
  const customerServicePage = readFileSync(resolve(root, publicChatPages[0]), 'utf8')

  assert.match(
    resumePage,
    /class="header-link mobile-scene-link" to="\/customer-service">智能客服<\/router-link>/
  )
  assert.match(
    customerServicePage,
    /class="header-link mobile-scene-link" to="\/personal-info">个人简历<\/router-link>/
  )

  for (const page of [resumePage, customerServicePage]) {
    assert.match(page, /\.header-link:not\(\.mobile-scene-link\)/)
    assert.match(
      page,
      /\.mobile-scene-link\s*\{[^}]*min-height:\s*36px;[^}]*display:\s*inline-flex;[^}]*\}/s
    )
  }
})
