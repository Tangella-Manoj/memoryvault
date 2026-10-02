import { useEffect, useState, useCallback } from 'react'
import { AlertCircle, RefreshCw, BarChart2 } from 'lucide-react'
import api from '../lib/api'
import SavingPatternChart from '../components/dashboard/SavingPatternChart'
import ContentTypePie from '../components/analytics/ContentTypePie'
import EmotionalContextBar from '../components/analytics/EmotionalContextBar'
import TagCloud from '../components/analytics/TagCloud'

export default function Analytics() {
  const [analytics, setAnalytics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const loadAnalytics = useCallback(() => {
    setLoading(true)
    setError(null)
    api
      .get('/vault/analytics')
      .then((res) => {
        setAnalytics(res.data?.data ?? null)
      })
      .catch(() => {
        setError('Failed to compute analytics. Please check your connection and try again.')
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadAnalytics()
  }, [loadAnalytics])

  if (loading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div>
          <div className="h-7 bg-slate-200 rounded w-36 mb-2" />
          <div className="h-4 bg-slate-100 rounded w-72" />
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {[...Array(3)].map((_, i) => (
            <div key={i} className="h-24 bg-slate-100 rounded-lg" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-64 bg-slate-100 rounded-lg" />
          ))}
        </div>
      </div>
    )
  }

  if (error || !analytics) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center">
        <AlertCircle className="w-10 h-10 text-red-400 mb-3" />
        <p className="text-slate-800 font-semibold mb-1">Unable to load analytics</p>
        <p className="text-sm text-slate-500 mb-5 max-w-sm">
          {error || 'No analytics data available at this time.'}
        </p>
        <button
          onClick={loadAnalytics}
          className="inline-flex items-center gap-2 text-sm font-semibold text-teal-600 hover:text-teal-700 border border-teal-300 px-4 py-2 rounded-lg hover:bg-teal-50 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
          Retry
        </button>
      </div>
    )
  }

  const intelligenceScore = (analytics.intelligenceScore ?? 0).toFixed(1)
  const avgImportance = (analytics.averageImportanceScore ?? 0).toFixed(2)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900 flex items-center gap-2.5">
          <BarChart2 className="w-6 h-6 text-teal-600" />
          <span>Analytics</span>
        </h1>
        <p className="text-sm text-slate-500 mt-1">How you save, and what your intelligence score says about it.</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Total items</span>
          <div className="text-3xl font-bold text-slate-900 mt-1">{analytics.totalItems ?? 0}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Intelligence score</span>
          <div className="text-3xl font-bold text-teal-700 mt-1">{intelligenceScore}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">Avg. importance</span>
          <div className="text-3xl font-bold text-slate-900 mt-1">{avgImportance}</div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <SavingPatternChart savesByDayOfWeek={analytics.savesByDayOfWeek ?? {}} />
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <h3 className="text-sm font-semibold text-slate-800 mb-4">Content types</h3>
          <ContentTypePie itemsByContentType={analytics.itemsByContentType ?? {}} />
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <h3 className="text-sm font-semibold text-slate-800 mb-4">Emotional context</h3>
          <EmotionalContextBar itemsByEmotionalContext={analytics.itemsByEmotionalContext ?? {}} />
        </div>
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
          <h3 className="text-sm font-semibold text-slate-800 mb-4">Top tags</h3>
          <TagCloud topTags={analytics.topTags ?? []} />
        </div>
      </div>
    </div>
  )
}
