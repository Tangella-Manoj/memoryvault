import { create } from 'zustand'

const STORAGE_KEY = 'memoryvault_auth'

function loadPersisted() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function persist(state) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // ignore storage failures
  }
}

const persisted = loadPersisted()

export const useAuthStore = create((set) => ({
  user: persisted?.user ?? null,
  accessToken: persisted?.accessToken ?? null,
  refreshToken: persisted?.refreshToken ?? null,

  login: (authResponse) => {
    const next = {
      user: {
        id: authResponse.userId,
        email: authResponse.email,
        displayName: authResponse.displayName,
      },
      accessToken: authResponse.accessToken,
      refreshToken: authResponse.refreshToken,
    }
    persist(next)
    set(next)
  },

  logout: () => {
    localStorage.removeItem(STORAGE_KEY)
    set({ user: null, accessToken: null, refreshToken: null })
  },
}))
