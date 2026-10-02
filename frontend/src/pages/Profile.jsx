import { useEffect, useState, useCallback } from 'react'
import { Copy, Check, Mail, AlertCircle, RefreshCw } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../lib/api'
import YouTubeIntegration from '../components/profile/YouTubeIntegration'
import NotificationSettings from '../components/profile/NotificationSettings'

export default function Profile() {
  const [profile, setProfile] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [copied, setCopied] = useState(false)

  const loadProfile = useCallback(() => {
    setLoading(true)
    setError(null)
    api
      .get('/users/me')
      .then((res) => {
        setProfile(res.data?.data ?? null)
      })
      .catch(() => {
        setError('Failed to load profile details. Please try again.')
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    loadProfile()
  }, [loadProfile])

  function copyVaultEmail() {
    if (!profile?.vaultEmail) return
    navigator.clipboard.writeText(profile.vaultEmail)
    setCopied(true)
    toast.success('Copied to clipboard')
    setTimeout(() => setCopied(false), 2000)
  }

  if (loading) {
    return (
      <div className="space-y-8 max-w-2xl animate-pulse">
        <div>
          <div className="h-7 bg-slate-200 rounded w-28 mb-2" />
          <div className="h-4 bg-slate-100 rounded w-52" />
        </div>
        <div className="h-36 bg-slate-100 rounded-xl" />
        <div className="h-44 bg-slate-100 rounded-xl" />
      </div>
    )
  }

  if (error || !profile) {
    return (
      <div className="flex flex-col items-center justify-center py-20 text-center max-w-2xl">
        <AlertCircle className="w-10 h-10 text-red-400 mb-3" />
        <p className="text-slate-800 font-semibold mb-1">Unable to load profile</p>
        <p className="text-sm text-slate-500 mb-5">{error || 'No profile information found.'}</p>
        <button
          onClick={loadProfile}
          className="inline-flex items-center gap-2 text-sm font-semibold text-teal-600 hover:text-teal-700 border border-teal-300 px-4 py-2 rounded-lg hover:bg-teal-50 transition-colors"
        >
          <RefreshCw className="w-4 h-4" />
          Try again
        </button>
      </div>
    )
  }

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Profile</h1>
        <p className="text-sm text-slate-500 mt-1">{profile.displayName} — {profile.email}</p>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-2xs">
        <div className="flex items-center gap-2 mb-2">
          <Mail className="w-4 h-4 text-teal-600" />
          <h2 className="font-semibold text-slate-900">Your personal vault email</h2>
        </div>
        <p className="text-sm text-slate-500 mb-3">
          Forward any WhatsApp link, newsletter, or article to this address and it will
          appear in your vault automatically.
        </p>
        <div className="flex items-center gap-2">
          <code className="flex-1 bg-slate-50 border border-slate-200 rounded-lg px-3.5 py-2 text-sm text-slate-800 font-mono">
            {profile.vaultEmail ?? 'Not yet generated'}
          </code>
          <button
            onClick={copyVaultEmail}
            disabled={!profile.vaultEmail}
            className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50 transition-colors cursor-pointer"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      </div>

      <YouTubeIntegration />
      <NotificationSettings />
    </div>
  )
}
