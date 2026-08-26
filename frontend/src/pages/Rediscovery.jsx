import { useEffect, useState } from 'react'
import { Sparkles, Check } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../lib/api'

export default function Rediscovery() {
  const [items, setItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [rediscovered, setRediscovered] = useState(new Set())

  useEffect(() => {
    api.get('/vault/forgotten').then((res) => setItems(res.data.data)).finally(() => setLoading(false))
  }, [])

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
    return <p className="text-sm text-slate-500">Digging through your forgotten saves…</p>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Rediscovery</h1>
        <p className="text-sm text-slate-500 mt-1">Things you saved and never came back to.</p>
      </div>

      {items.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-lg p-12 text-center">
          <Sparkles className="w-8 h-8 text-teal-400 mx-auto mb-3" />
          <p className="text-slate-600 font-medium">Nothing forgotten yet</p>
          <p className="text-sm text-slate-500 mt-1">Give it 30 days — this is where old saves come back to life.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {items.map((item) => {
            const isDone = rediscovered.has(item.id)
            return (
              <div key={item.id} className="bg-white border border-slate-200 rounded-lg p-4 flex flex-col gap-3">
                {item.ogImageUrl && (
                  <img src={item.ogImageUrl} alt="" className="w-full h-36 object-cover rounded-md" />
                )}
                <div>
                  <a href={item.url} target="_blank" rel="noreferrer" className="font-medium text-slate-900 hover:underline">
                    {item.title ?? item.url}
                  </a>
                  {item.summary && <p className="text-sm text-slate-500 mt-1 line-clamp-2">{item.summary}</p>}
                </div>
                <p className="text-xs text-slate-400">
                  Saved {new Date(item.savedAt).toLocaleDateString()} — importance {item.importanceScore}
                </p>
                <button
                  onClick={() => handleRediscover(item.id)}
                  disabled={isDone}
                  className={`text-sm rounded-md py-2 flex items-center justify-center gap-1.5 transition-colors ${
                    isDone
                      ? 'bg-teal-50 text-teal-700'
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
