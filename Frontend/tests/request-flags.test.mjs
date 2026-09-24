import assert from 'node:assert/strict'
import test from 'node:test'

import { getRequestFlags } from '../src/utils/request-flags.ts'

test('reads anonymous request flags from the Axios config', () => {
  assert.deepEqual(
    getRequestFlags({ isToken: false, repeatSubmit: false }),
    { skipToken: true, skipRepeatSubmit: true }
  )
})

test('forces public API requests to be anonymous even when a token exists', () => {
  assert.deepEqual(
    getRequestFlags({ url: '/public/ai-chat/scenes' }),
    { skipToken: true, skipRepeatSubmit: true }
  )
})

test('keeps compatibility with legacy header flags and defaults to authenticated requests', () => {
  assert.deepEqual(
    getRequestFlags({ headers: { isToken: false, repeatSubmit: false } }),
    { skipToken: true, skipRepeatSubmit: true }
  )
  assert.deepEqual(
    getRequestFlags({ headers: {} }),
    { skipToken: false, skipRepeatSubmit: false }
  )
})
