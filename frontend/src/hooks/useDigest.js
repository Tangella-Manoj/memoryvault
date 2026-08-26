import { useEffect, useState } from 'react'
import api from '../lib/api'

export function useDigest() {
  const [digest, setDigest] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    api.get('/vault/digest/today')
      .then((response) => {
        if (!cancelled) setDigest(response.data.data)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  return { digest, loading }
}
