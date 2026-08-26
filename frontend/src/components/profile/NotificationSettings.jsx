import { useEffect, useState } from 'react'
import { Bell, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../../lib/api'

const VAPID_PUBLIC_KEY = import.meta.env.VITE_VAPID_PUBLIC_KEY

const HOUR_LABELS = Array.from({ length: 24 }, (_, h) => {
  const period = h < 12 ? 'AM' : 'PM'
  const hour12 = h % 12 === 0 ? 12 : h % 12
  return `${hour12}:00 ${period}`
})

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)))
}

export default function NotificationSettings() {
  const [prefs, setPrefs] = useState(null)
  const [recalculating, setRecalculating] = useState(false)

  function loadPrefs() {
    api.get('/notifications/preferences').then((res) => setPrefs(res.data.data))
  }

  useEffect(() => {
    loadPrefs()
  }, [])

  async function handleToggle() {
    try {
      if (prefs.notificationsEnabled) {
        await api.post('/notifications/unsubscribe')
        loadPrefs()
        return
      }
      if (Notification.permission !== 'granted') {
        const permission = await Notification.requestPermission()
        if (permission !== 'granted') {
          toast.error('Browser notification permission was not granted')
          return
        }
      }
      const reg = await navigator.serviceWorker.ready
      const subscription = await reg.pushManager.getSubscription()
        ?? await reg.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
        })
      const res = await api.post('/notifications/subscribe', { subscription: JSON.stringify(subscription) })
      setPrefs(res.data.data)
    } catch {
      toast.error('Could not update notification settings')
    }
  }

  async function handleRecalculate() {
    setRecalculating(true)
    try {
      const res = await api.post('/notifications/recalculate')
      setPrefs((prev) => ({ ...prev, optimalHour: res.data.data.optimalHour }))
      toast.success('Optimal time recalculated')
    } catch {
      toast.error('Could not recalculate')
    } finally {
      setRecalculating(false)
    }
  }

  if (!prefs) return null

  return (
    <div className="bg-white border border-slate-200 rounded-lg p-5">
      <div className="flex items-center gap-2 mb-2">
        <Bell className="w-4 h-4 text-teal-600" />
        <h2 className="font-medium text-slate-900">Resurfaced reminders</h2>
      </div>
      <p className="text-sm text-slate-500 mb-3">
        We send one notification a day, around the time you're most likely to revisit saved items.
      </p>
      <div className="space-y-3">
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <input type="checkbox" checked={prefs.notificationsEnabled} onChange={handleToggle} />
          Enable notifications
        </label>
        <p className="text-sm text-slate-600">
          Current optimal time: <span className="font-medium text-slate-900">{HOUR_LABELS[prefs.optimalHour] ?? '8:00 AM'}</span>
          {' '}<span className="text-slate-400">UTC</span>
        </p>
        <button
          onClick={handleRecalculate}
          disabled={recalculating}
          className="flex items-center gap-1.5 text-sm px-3 py-2 rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50 disabled:opacity-50"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${recalculating ? 'animate-spin' : ''}`} />
          Recalculate now
        </button>
      </div>
    </div>
  )
}
