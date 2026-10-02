import test from 'node:test'
import assert from 'node:assert/strict'

// Provide a mock localStorage for the Node.js test environment
const mockStorage = new Map()
globalThis.localStorage = {
  getItem: (key) => mockStorage.get(key) ?? null,
  setItem: (key, val) => mockStorage.set(key, String(val)),
  removeItem: (key) => mockStorage.delete(key),
  clear: () => mockStorage.clear(),
}

// Import authStore
const { useAuthStore } = await import('../src/stores/authStore.js')

test('authStore: login sets in-memory tokens and persists user + refreshToken', () => {
  mockStorage.clear()
  const authResponse = {
    userId: 1,
    email: 'test@example.com',
    displayName: 'Test User',
    accessToken: 'access-jwt-secret-xyz',
    refreshToken: 'refresh-token-raw-123',
  }

  useAuthStore.getState().login(authResponse)

  const state = useAuthStore.getState()
  assert.equal(state.user?.id, 1)
  assert.equal(state.user?.email, 'test@example.com')
  assert.equal(state.accessToken, 'access-jwt-secret-xyz')
  assert.equal(state.refreshToken, 'refresh-token-raw-123')

  // Verify localStorage persistence
  const persistedRaw = mockStorage.get('memoryvault_auth')
  assert.ok(persistedRaw, 'Persisted JSON should exist in localStorage')

  const persisted = JSON.parse(persistedRaw)
  assert.equal(persisted.user?.email, 'test@example.com')
  assert.equal(persisted.refreshToken, 'refresh-token-raw-123')

  // CRITICAL SECURITY ASSERTION: accessToken MUST NOT be in localStorage
  assert.equal(
    persisted.accessToken,
    undefined,
    'accessToken must not be persisted to localStorage (XSS prevention)'
  )
})

test('authStore: setAccessToken updates in-memory token without modifying localStorage', () => {
  useAuthStore.getState().setAccessToken('updated-access-token-456')

  const state = useAuthStore.getState()
  assert.equal(state.accessToken, 'updated-access-token-456')

  const persisted = JSON.parse(mockStorage.get('memoryvault_auth'))
  assert.equal(persisted.accessToken, undefined)
})

test('authStore: logout clears all state and localStorage item', () => {
  useAuthStore.getState().logout()

  const state = useAuthStore.getState()
  assert.equal(state.user, null)
  assert.equal(state.accessToken, null)
  assert.equal(state.refreshToken, null)
  assert.equal(mockStorage.has('memoryvault_auth'), false)
})
