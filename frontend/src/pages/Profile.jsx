import { useEffect, useState } from 'react'
import { Copy, Check, Mail } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../lib/api'

export default function Profile() {
  const [profile, setProfile] = useState(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    api.get('/users/me').then((res) => setProfile(res.data.data))
  }, [])

  function copyVaultEmail() {
    if (!profile?.vaultEmail) return
    navigator.clipboard.writeText(profile.vaultEmail)
    setCopied(true)
    toast.success('Copied to clipboard')
    setTimeout(() => setCopied(false), 2000)
  }

  if (!profile) {
    return <p className="text-sm text-slate-500">Loading profile…</p>
  }

  return (
    <div className="space-y-8 max-w-2xl">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Profile</h1>
        <p className="text-sm text-slate-500 mt-1">{profile.displayName} — {profile.email}</p>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg p-5">
        <div className="flex items-center gap-2 mb-2">
          <Mail className="w-4 h-4 text-teal-600" />
          <h2 className="font-medium text-slate-900">Your personal vault email</h2>
        </div>
        <p className="text-sm text-slate-500 mb-3">
          Forward any WhatsApp link, newsletter, or article to this address and it will
          appear in your vault automatically.
        </p>
        <div className="flex items-center gap-2">
          <code className="flex-1 bg-slate-50 border border-slate-200 rounded-md px-3 py-2 text-sm text-slate-800">
            {profile.vaultEmail ?? 'Not yet generated'}
          </code>
          <button
            onClick={copyVaultEmail}
            disabled={!profile.vaultEmail}
            className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm bg-teal-600 text-white hover:bg-teal-700 disabled:opacity-50"
          >
            {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      </div>
    </div>
  )
}
