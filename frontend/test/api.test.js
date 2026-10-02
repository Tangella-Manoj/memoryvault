import test from 'node:test'
import assert from 'node:assert/strict'

// Provide a mock localStorage for Node.js
const mockStorage = new Map()
globalThis.localStorage = {
  getItem: (key) => mockStorage.get(key) ?? null,
  setItem: (key, val) => mockStorage.set(key, String(val)),
  removeItem: (key) => mockStorage.delete(key),
  clear: () => mockStorage.clear(),
}

const { useAuthStore } = await import('../src/stores/authStore.js')
const { default: api } = await import('../src/lib/api.js')

test('api: request interceptor injects Bearer token when accessToken exists', async () => {
  useAuthStore.getState().setAccessToken('mock-token-xyz-123')

  // Find the request interceptor
  const requestInterceptor = api.interceptors.request.handlers[0].fulfilled
  const config = { headers: {} }

  const modified = requestInterceptor(config)
  assert.equal(modified.headers.Authorization, 'Bearer mock-token-xyz-123')
})

test('api: request interceptor does not inject Authorization when token is null', async () => {
  useAuthStore.getState().logout()

  const requestInterceptor = api.interceptors.request.handlers[0].fulfilled
  const config = { headers: {} }

  const modified = requestInterceptor(config)
  assert.equal(modified.headers.Authorization, undefined)
})

test('api: response interceptor bypasses auth routes on 401 without redirect', async () => {
  const responseInterceptorError = api.interceptors.response.handlers[0].rejected

  const authError = {
    config: { url: '/auth/login' },
    response: { status: 401, data: { message: 'Invalid credentials' } },
  }

  await assert.rejects(
    async () => responseInterceptorError(authError),
    (err) => {
      assert.equal(err.response?.status, 401)
      assert.equal(err.config?.url, '/auth/login')
      return true
    },
    'Auth route errors must be rejected directly so the page can render error banners'
  )
})
