import { useEffect, useState } from 'react'
import api from '../lib/api'
import SavingPatternChart from '../components/dashboard/SavingPatternChart'
import ContentTypePie from '../components/analytics/ContentTypePie'
import EmotionalContextBar from '../components/analytics/EmotionalContextBar'
import TagCloud from '../components/analytics/TagCloud'

export default function Analytics() {
  const [analytics, setAnalytics] = useState(null)

  useEffect(() => {
    api.get('/vault/analytics').then((res) => setAnalytics(res.data.data))
  }, [])

  if (!analytics) {
    return <p className="text-sm text-slate-500">Crunching the numbers…</p>
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Analytics</h1>
        <p className="text-sm text-slate-500 mt-1">How you save, and what your intelligence score says about it.</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <span className="text-sm text-slate-500">Total items</span>
          <div className="text-3xl font-semibold text-slate-900 mt-1">{analytics.totalItems}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <span className="text-sm text-slate-500">Intelligence score</span>
          <div className="text-3xl font-semibold text-teal-700 mt-1">{analytics.intelligenceScore.toFixed(1)}</div>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <span className="text-sm text-slate-500">Avg. importance</span>
          <div className="text-3xl font-semibold text-slate-900 mt-1">{analytics.averageImportanceScore.toFixed(2)}</div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <SavingPatternChart savesByDayOfWeek={analytics.savesByDayOfWeek} />
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-slate-700 mb-4">Content types</h3>
          <ContentTypePie itemsByContentType={analytics.itemsByContentType} />
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-slate-700 mb-4">Emotional context</h3>
          <EmotionalContextBar itemsByEmotionalContext={analytics.itemsByEmotionalContext} />
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <h3 className="text-sm font-medium text-slate-700 mb-4">Top tags</h3>
          <TagCloud topTags={analytics.topTags} />
        </div>
      </div>
    </div>
  )
}
