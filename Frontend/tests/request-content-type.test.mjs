import assert from 'node:assert/strict'
import test from 'node:test'

import { normalizeFormDataRequest } from '../src/utils/request-content-type.ts'

test('clears the JSON content type before Axios transforms FormData', () => {
  let contentType = 'application/json;charset=utf-8'
  const config = {
    data: new FormData(),
    headers: {
      setContentType(value) {
        contentType = value
      }
    }
  }

  normalizeFormDataRequest(config)

  assert.equal(contentType, undefined)
})

test('keeps the JSON content type for regular request bodies', () => {
  let contentType = 'application/json;charset=utf-8'
  const config = {
    data: { name: 'knowledge.txt' },
    headers: {
      setContentType(value) {
        contentType = value
      }
    }
  }

  normalizeFormDataRequest(config)

  assert.equal(contentType, 'application/json;charset=utf-8')
})
