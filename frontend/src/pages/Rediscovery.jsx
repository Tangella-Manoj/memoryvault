import { useEffect, useState, useCallback } from 'react'
import { Sparkles, Check, AlertCircle, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../lib/api'

export default function Rediscovery() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [rediscovered, setRediscovered] = useState(new Set())

  const loadForgotten = useCallback(() => {
    setLoading(true)
    setError(null)
    api
      .get('/vault/forgotten')
      .then((res) => {
        setItems(res.data?.data ?? [])
      })
      .catch(() => {
        setError('Failed to load rediscovery items. Please try again.')
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadForgotten()
  }, [loadForgotten])

  async function handleRediscover(id) {
    try {
      await api.post(`/vault/${id}/rediscover`)
      setRediscovered((prev) => new Set(prev).add(id))
      toast.success('Marked as rediscovered')
    } catch {
      toast.error('Could not update this item')
    }
  }

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div>
          <div className="h-7 bg-slate-200 rounded w-40 mb-2" />
          <div className="h-4 bg-slate-100 rounded w-64" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-44 bg-slate-100 rounded-lg" />
          ))}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <AlertCircle className="w-10 h-10 text-red-400 mb-3" />
        <p className="text-slate-800 font-semibold mb-1">Failed to load rediscovery feed</p>
        <p className="text-sm text-slate-500 mb-5">{error}</p>
        <button
          onClick={loadForgotten}
          className="inline-flex items-center gap-2 text-sm font-semibold text-teal-600 hover:text-teal-700 border border-teal-300 px-4 py-2 rounded-lg hover:bg-teal-50 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Rediscovery</h1>
        <p className="text-sm text-slate-500 mt-1">Things you saved and never came back to.</p>
      </div>

      {items.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-xl p-12 text-center shadow-2xs">
          <Sparkles className="w-8 h-8 text-teal-500 mx-auto mb-3" />
          <p className="text-slate-700 font-medium">Nothing forgotten yet</p>
          <p className="text-sm text-slate-500 mt-1">Give it 30 days — this is where old saves come back to life.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {items.map((item) => {
            const isDone = rediscovered.has(item.id)
            return (
              <div key={item.id} className="bg-white border border-slate-200 rounded-xl p-5 flex flex-col gap-3 shadow-2xs">
                {item.ogImageUrl && (
                  <img src={item.ogImageUrl} alt="" className="w-full h-36 object-cover rounded-lg" />
                )}
                <div className="flex-1">
                  <a href={item.url} target="_blank" rel="noreferrer" className="font-semibold text-slate-900 hover:text-teal-600 hover:underline">
                    {item.title ?? item.url}
                  </a>
                  {item.summary && <p className="text-sm text-slate-600 mt-1 line-clamp-2">{item.summary}</p>}
                </div>
                <p className="text-xs text-slate-400">
                  Saved {new Date(item.savedAt).toLocaleDateString()} — importance {item.importanceScore}
                </p>
                <button
                  onClick={() => handleRediscover(item.id)}
                  disabled={isDone}
                  className={`text-sm rounded-xl py-2.5 font-medium flex items-center justify-center gap-1.5 transition-colors cursor-pointer ${
                    isDone
                      ? 'bg-teal-50 text-teal-700 border border-teal-200'
                      : 'bg-slate-900 text-white hover:bg-slate-800'
                  }`}
                >
                  {isDone ? (
                    <>
                      <Check className="w-4 h-4" /> Rediscovered
                    </>
                  ) : (
                    'Mark as rediscovered'
                  )}
                </button>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
