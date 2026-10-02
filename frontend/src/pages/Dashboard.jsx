import { useEffect, useState } from 'react'
import { Library, Sparkles, TrendingUp, Clock, AlertCircle, RefreshCw } from 'lucide-react'
import api from '../lib/api'
import MetricCard from '../components/dashboard/MetricCard'
import SavingPatternChart from '../components/dashboard/SavingPatternChart'
import ResurfaceFeed from '../components/dashboard/ResurfaceFeed'

export default function Dashboard() {
  const [analytics, setAnalytics] = useState(null)
  const [resurfaceItems, setResurfaceItems] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  function loadData() {
    setLoading(true)
    setError(null)
    Promise.all([
      api.get('/vault/analytics'),
      api.get('/vault/resurface', { params: { limit: 6 } }),
    ])
      .then(([analyticsRes, resurfaceRes]) => {
        setAnalytics(analyticsRes.data.data)
        setResurfaceItems(resurfaceRes.data.data ?? [])
      })
      .catch(() => {
        setError('Failed to load dashboard data. Please check your connection and try again.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => { loadData() }, [])

  if (loading) {
    return (
      <div className="space-y-8 animate-pulse">
        <div>
          <div className="h-7 bg-slate-200 rounded w-32 mb-2" />
          <div className="h-4 bg-slate-100 rounded w-64" />
        </div>
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-24 bg-slate-100 rounded-xl" />
          ))}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <AlertCircle className="w-10 h-10 text-red-400 mb-4" />
        <p className="text-slate-700 font-medium mb-1">Something went wrong</p>
        <p className="text-sm text-slate-500 mb-5">{error}</p>
        <button
          onClick={loadData}
          className="inline-flex items-center gap-2 text-sm font-semibold text-teal-600 hover:text-teal-700 border border-teal-300 px-4 py-2 rounded-lg hover:bg-teal-50 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1">What you've saved, and what's worth revisiting.</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard label="Total items" value={analytics?.totalItems ?? 0} icon={Library} />
        <MetricCard label="Intelligence score" value={(analytics?.intelligenceScore ?? 0).toFixed(1)} icon={TrendingUp} />
        <MetricCard label="Avg. importance" value={(analytics?.averageImportanceScore ?? 0).toFixed(2)} icon={Sparkles} />
        <MetricCard label="Resurfaced today" value={resurfaceItems.length} icon={Clock} />
      </div>

      <div>
        <h2 className="text-sm font-medium text-slate-700 mb-3">Worth another look</h2>
        <ResurfaceFeed items={resurfaceItems} />
      </div>

      <SavingPatternChart savesByDayOfWeek={analytics?.savesByDayOfWeek ?? {}} />
    </div>
  )
}
