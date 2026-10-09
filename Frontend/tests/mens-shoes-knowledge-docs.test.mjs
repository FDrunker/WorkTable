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

test('mens shoes knowledge documents cover sales, quality, and return rules', () => {
  const sales = read('docs/knowledge/mens-shoes/01-男鞋售前推销话术.md')
  const quality = read('docs/knowledge/mens-shoes/02-男鞋售后质量问题处理.md')
  const returns = read('docs/knowledge/mens-shoes/03-男鞋退换货规则.md')

  assert.match(sales, /^# 男鞋售前推销话术/m)
  assert.match(sales, /核心卖点表达/)
  assert.match(sales, /常见异议处理/)

  assert.match(quality, /^# 男鞋售后质量问题处理/m)
  assert.match(quality, /鞋底或鞋面开胶/)
  assert.match(quality, /升级条件/)

  assert.match(returns, /^# 男鞋退换货规则/m)
  assert.match(returns, /七天无理由退货/)
  assert.match(returns, /质量问题退换货/)
  assert.match(returns, /退换货流程/)
})
