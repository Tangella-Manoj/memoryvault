import { useEffect, useState } from 'react'
import { Library, Sparkles, TrendingUp, Clock } from 'lucide-react'
import api from '../lib/api'
import MetricCard from '../components/dashboard/MetricCard'
import SavingPatternChart from '../components/dashboard/SavingPatternChart'
import ResurfaceFeed from '../components/dashboard/ResurfaceFeed'

export default function Dashboard() {
  const [analytics, setAnalytics] = useState(null)
  const [resurfaceItems, setResurfaceItems] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      api.get('/vault/analytics'),
      api.get('/vault/resurface', { params: { limit: 6 } }),
    ])
      .then(([analyticsRes, resurfaceRes]) => {
        setAnalytics(analyticsRes.data.data)
        setResurfaceItems(resurfaceRes.data.data)
      })
      .finally(() => setLoading(false))
  }, [])

  if (loading) {
    return <p className="text-sm text-slate-500">Loading your dashboard…</p>
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
        <p className="text-sm text-slate-500 mt-1">What you've saved, and what's worth revisiting.</p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard label="Total items" value={analytics.totalItems} icon={Library} />
        <MetricCard label="Intelligence score" value={analytics.intelligenceScore.toFixed(1)} icon={TrendingUp} />
        <MetricCard label="Avg. importance" value={analytics.averageImportanceScore.toFixed(2)} icon={Sparkles} />
        <MetricCard label="Resurfaced today" value={resurfaceItems.length} icon={Clock} />
      </div>

      <div>
        <h2 className="text-sm font-medium text-slate-700 mb-3">Worth another look</h2>
        <ResurfaceFeed items={resurfaceItems} />
      </div>

      <SavingPatternChart savesByDayOfWeek={analytics.savesByDayOfWeek} />
    </div>
  )
}
