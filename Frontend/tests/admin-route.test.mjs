import assert from 'node:assert/strict'
import test from 'node:test'

import { prefixAdminRoutes, withAdminPrefix } from '../src/utils/admin-route.ts'

test('prefixes frontend routes with admin while preserving external links', () => {
  assert.equal(withAdminPrefix('/index'), '/admin/index')
  assert.equal(withAdminPrefix('/system/user'), '/admin/system/user')
  assert.equal(withAdminPrefix('/admin/index'), '/admin/index')
  assert.equal(withAdminPrefix('system/user'), '/admin/system/user')
  assert.equal(withAdminPrefix('https://example.com/docs'), 'https://example.com/docs')
})

test('prefixes dynamic menu roots and active menu metadata without mutating the source', () => {
  const source = [
    {
      path: '/knowledge-base',
      meta: { title: '知识库' },
      children: [
        {
          path: 'index',
          meta: { title: '知识库', activeMenu: '/knowledge-base' }
        }
      ]
    }
  ]

  const result = prefixAdminRoutes(source)

  assert.equal(result[0].path, '/admin/knowledge-base')
  assert.equal(result[0].children[0].path, 'index')
  assert.equal(result[0].children[0].meta.activeMenu, '/admin/knowledge-base')
  assert.equal(source[0].path, '/knowledge-base')
  assert.equal(source[0].children[0].meta.activeMenu, '/knowledge-base')
})
