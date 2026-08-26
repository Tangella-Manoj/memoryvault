import { useEffect, useState } from 'react'
import { PlaySquare, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../../lib/api'

export default function YouTubeIntegration() {
  const [status, setStatus] = useState(null)
  const [syncing, setSyncing] = useState(false)

  function loadStatus() {
    api.get('/integrations/youtube/status').then((res) => setStatus(res.data.data))
  }

  useEffect(() => {
    loadStatus()

    const params = new URLSearchParams(window.location.search)
    const youtubeParam = params.get('youtube')
    if (youtubeParam === 'connected') {
      toast.success('YouTube connected')
    } else if (youtubeParam === 'error') {
      toast.error('Could not connect YouTube')
    }
  }, [])

  async function handleConnect() {
    try {
      const res = await api.get('/integrations/youtube/connect')
      window.location.href = res.data.data.authorizationUrl
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'YouTube integration is not configured on this server')
    }
  }

  async function handleSync() {
    setSyncing(true)
    try {
      const res = await api.post('/integrations/youtube/sync')
      toast.success(`Synced ${res.data.data.itemsCreated} new video(s)`)
      loadStatus()
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Sync failed')
    } finally {
      setSyncing(false)
    }
  }

  async function handleToggle() {
    const next = !status.syncEnabled
    const res = await api.post('/integrations/youtube/toggle', null, { params: { enabled: next } })
    setStatus(res.data.data)
  }

  if (!status) return null

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex items-center gap-2 mb-2">
        <PlaySquare className="w-4 h-4 text-red-600" />
        <h2 className="font-medium text-slate-900">YouTube</h2>
      </div>

      {!status.connected ? (
        <>
          <p className="text-sm text-slate-500 mb-3">
            Connect your account to automatically sync liked videos and Watch Later every 6 hours.
          </p>
          <button
            onClick={handleConnect}
            className="text-sm px-3 py-2 rounded-md bg-slate-900 text-white hover:bg-slate-800"
          >
            Connect YouTube
          </button>
        </>
      ) : (
        <div className="space-y-3">
          <div className="text-sm text-slate-600 space-y-1">
            <p>Status: <span className="text-teal-700 font-medium">Connected</span></p>
            <p>Last synced: {status.lastSyncedAt ? new Date(status.lastSyncedAt).toLocaleString() : 'never'}</p>
            <p>Total videos synced: {status.totalItemsSynced}</p>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={handleSync}
              disabled={syncing}
              className="flex items-center gap-1.5 text-sm px-3 py-2 rounded-md bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${syncing ? 'animate-spin' : ''}`} />
              {syncing ? 'Syncing…' : 'Sync now'}
            </button>
            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={status.syncEnabled} onChange={handleToggle} />
              Auto-sync every 6 hours
            </label>
          </div>
        </div>
      )}
    </div>
  )
}
