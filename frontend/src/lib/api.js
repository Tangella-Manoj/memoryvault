import axios from 'axios'
import { useAuthStore } from '../stores/authStore.js'

const api = axios.create({
  baseURL: (typeof import.meta !== 'undefined' && import.meta.env?.VITE_API_BASE_URL) || 'http://localhost:8091/api',
})

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshInFlight = null

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config
    const url = originalRequest?.url || ''
    const isAuthRoute = url.includes('/auth/')

    // If the failure occurred on an auth endpoint (login, register, forgot-password, reset-password),
    // NEVER redirect or clear state — let the calling component catch the error and show the exact error message.
    if (isAuthRoute) {
      return Promise.reject(error)
    }

    // For authenticated API routes that receive 401, attempt silent token refresh once
    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true
      const storedRefreshToken = useAuthStore.getState().refreshToken

      if (storedRefreshToken) {
        try {
          if (!refreshInFlight) {
            refreshInFlight = axios
              .post(`${api.defaults.baseURL}/auth/refresh`, {
                refreshToken: storedRefreshToken,
              })
              .then((res) => {
                const data = res.data?.data
                if (data) {
                  useAuthStore.getState().login(data)
                  return data.accessToken
                }
                throw new Error('No token returned in refresh response')
              })
              .finally(() => {
                refreshInFlight = null
              })
          }

          const newAccessToken = await refreshInFlight
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
          return api(originalRequest)
        } catch (refreshErr) {
          useAuthStore.getState().logout()
          if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
            window.location.href = '/login'
          }
          return Promise.reject(refreshErr)
        }
      } else {
        useAuthStore.getState().logout()
        if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
          window.location.href = '/login'
        }
      }
    }

    return Promise.reject(error)
  }
)

export default api
