import { useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { useAuthStore } from '../stores/authStore'
import api from '../lib/api'

export function useAuthGuard() {
  const { accessToken, refreshToken, login, logout } = useAuthStore()
  const [isRestoring, setIsRestoring] = useState(!accessToken && !!refreshToken)
  const navigate = useNavigate()

  useEffect(() => {
    // Case 1: No credentials at all -> immediate redirect to login
    if (!accessToken && !refreshToken) {
      navigate('/login', { replace: true })
      return
    }

    // Case 2: In-memory accessToken missing, but refreshToken exists -> restore session via silent refresh
    if (!accessToken && refreshToken) {
      let isMounted = true
      api.post('/auth/refresh', { refreshToken })
        .then((res) => {
          if (isMounted) {
            const data = res.data?.data
            if (data) {
              login(data)
            } else {
              logout()
              navigate('/login', { replace: true })
            }
          }
        })
        .catch(() => {
          if (isMounted) {
            logout()
            navigate('/login', { replace: true })
          }
        })
        .finally(() => {
          if (isMounted) {
            setIsRestoring(false)
          }
        })

      return () => {
        isMounted = false
      }
    }
  }, [accessToken, refreshToken, login, logout, navigate])

  return {
    isAuthenticated: !!accessToken,
    isRestoring,
  }
}
