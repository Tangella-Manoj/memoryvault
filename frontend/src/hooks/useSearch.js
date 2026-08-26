import { useCallback, useState } from 'react'
import api from '../lib/api'

export function useSearch() {
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const search = useCallback(async (query) => {
    if (!query?.trim()) {
      setResults([])
      return
    }
    setLoading(true)
    setError(null)
    try {
      const response = await api.get('/vault/search', { params: { q: query } })
      setResults(response.data.data)
    } catch (err) {
      setError(err.response?.data?.message ?? 'Search failed')
    } finally {
      setLoading(false)
    }
  }, [])

  return { results, loading, error, search }
}
