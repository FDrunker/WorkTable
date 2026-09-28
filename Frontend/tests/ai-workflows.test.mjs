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

test('resume AI and customer service AI use separate public pages and APIs', () => {
  const router = read('Frontend/src/router/index.ts')
  assert.match(router, /views\/public\/resume\/index\.vue/)
  assert.match(router, /views\/public\/customer-service\/index\.vue/)
  assert.match(read('Frontend/src/views/public/resume/index.vue'), /@\/api\/publicResume/)
  assert.match(
    read('Frontend/src/views/public/customer-service/index.vue'),
    /@\/api\/publicCustomerService/
  )
})

test('customer service pipeline classifies, preanalyzes, and persists complex tickets', () => {
  const service = read(
    'Backend/ruoyi-knowledge/src/main/java/com/ruoyi/knowledge/service/CustomerServiceAiChatService.java'
  )
  assert.match(service, /你是一个客服工单分类专家/)
  assert.match(service, /standard.*complex/s)
  assert.match(service, /preanalyze/)
  assert.match(service, /createComplexTicket/)
  assert.match(service, /已记录您的问题，等待人工给您处理/)
})

test('ticket handling page exposes approve, modify-approve, and escalate actions', () => {
  const page = read('Frontend/src/views/knowledge/customerServiceTicket/index.vue')
  assert.match(page, />\s*批准\s*</)
  assert.match(page, /修改后批准/)
  assert.match(page, /升级处理/)
  assert.match(page, /AI 预分析/)
  assert.match(page, /回复内容/)
})

test('split migration defines the human processing ticket table', () => {
  const sql = read('Backend/sql/resume-customer-service-split.sql')
  assert.match(sql, /create table if not exists ai_customer_service_ticket/)
  assert.match(sql, /preanalysis_summary/)
  assert.match(sql, /preanalysis_solution/)
  assert.match(sql, /suggested_reply/)
  assert.match(sql, /customer-service:ticket:edit/)
})
