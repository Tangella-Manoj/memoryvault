import { useEffect, useState } from 'react'
import { Bell, X } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../../lib/api'

const DISMISSED_KEY = 'memoryvault_notif_prompt_dismissed'
const VAPID_PUBLIC_KEY = import.meta.env.VITE_VAPID_PUBLIC_KEY

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4)
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/')
  const rawData = atob(base64)
  return Uint8Array.from([...rawData].map((c) => c.charCodeAt(0)))
}

export default function NotificationPermissionModal() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!('serviceWorker' in navigator) || !('PushManager' in window) || !VAPID_PUBLIC_KEY) return
    if (Notification.permission !== 'default') return
    if (localStorage.getItem(DISMISSED_KEY)) return
    setVisible(true)
  }, [])

  function dismiss() {
    localStorage.setItem(DISMISSED_KEY, '1')
    setVisible(false)
  }

  async function handleAllow() {
    try {
      const permission = await Notification.requestPermission()
      if (permission !== 'granted') {
        dismiss()
        return
      }
      const reg = await navigator.serviceWorker.ready
      const subscription = await reg.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
      })
      await api.post('/notifications/subscribe', { subscription: JSON.stringify(subscription) })
      toast.success('Notifications enabled')
    } catch {
      toast.error('Could not enable notifications')
    } finally {
      dismiss()
    }
  }

  if (!visible) return null

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
      <div className="bg-white rounded-lg shadow-xl max-w-sm w-full p-6 relative">
        <button onClick={dismiss} className="absolute top-3 right-3 text-slate-400 hover:text-slate-600">
          <X className="w-4 h-4" />
        </button>
        <div className="w-10 h-10 rounded-full bg-teal-50 flex items-center justify-center mb-4">
          <Bell className="w-5 h-5 text-teal-600" />
        </div>
        <h2 className="font-semibold text-slate-900 mb-1.5">Get resurfaced reminders</h2>
        <p className="text-sm text-slate-500 mb-5">
          MemoryVault learns the time of day you're most likely to revisit saved items, then sends
          one notification around that time with 3 things you saved and forgot. No spam — just that.
        </p>
        <div className="flex gap-2">
          <button
            onClick={dismiss}
            className="flex-1 text-sm px-3 py-2 rounded-md border border-slate-200 text-slate-600 hover:bg-slate-50"
          >
            Not now
          </button>
          <button
            onClick={handleAllow}
            className="flex-1 text-sm px-3 py-2 rounded-md bg-teal-600 text-white hover:bg-teal-700"
          >
            Enable
          </button>
        </div>
      </div>
    </div>
  )
}
