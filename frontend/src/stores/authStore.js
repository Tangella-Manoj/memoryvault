import { create } from 'zustand'

const STORAGE_KEY = 'memoryvault_auth'

/** Only user identity and refreshToken survive page reloads. The access token
 * lives in memory only — it is short-lived (15 min) and re-acquired automatically
 * via silent refresh on the first 401. Keeping it out of localStorage limits the
 * XSS blast radius: a script that exfiltrates localStorage gets a refresh token
 * (bad) but not an immediately usable access token (slightly less bad).
 */
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
    // Deliberately exclude accessToken — memory only.
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ user: state.user, refreshToken: state.refreshToken })
    )
  } catch {
    // ignore storage failures
  }
}

const persisted = loadPersisted()

export const useAuthStore = create((set) => ({
  user: persisted?.user ?? null,
  accessToken: null,                        // memory-only — intentionally not rehydrated
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

  /** Called after a silent refresh — only updates the in-memory access token. */
  setAccessToken: (accessToken) => set({ accessToken }),

  logout: () => {
    localStorage.removeItem(STORAGE_KEY)
    set({ user: null, accessToken: null, refreshToken: null })
  },
}))
