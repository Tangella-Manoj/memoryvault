import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Loader2, CheckCircle2, XCircle } from 'lucide-react'
import api from '../lib/api'
import { useAuthStore } from '../stores/authStore'

export default function ShareTarget() {
  const [status, setStatus] = useState('saving') // saving | success | error
  const [item, setItem] = useState(null)
  const [errorMessage, setErrorMessage] = useState('')
  const navigate = useNavigate()
  const accessToken = useAuthStore((s) => s.accessToken)

  useEffect(() => {
    if (!accessToken) {
      // Not logged in — bounce to login, then back here isn't tracked (out of scope
      // for a share-target flow); simplest correct behavior is to just require login first.
      navigate('/login', { replace: true })
      return
    }

    const params = new URLSearchParams(window.location.search)
    const title = params.get('title') || ''
    const text = params.get('text') || ''
    const url = params.get('url') || ''

    const body = new URLSearchParams({ title, text, url })

    api
      .post('/vault/share-target', body, {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      })
      .then((response) => {
        setItem(response.data.data)
        setStatus('success')
        if (window.opener) {
          setTimeout(() => {
            try {
              window.close()
            } catch {
              navigate('/dashboard', { replace: true })
            }
          }, 1500)
        } else {
          setTimeout(() => navigate('/dashboard', { replace: true }), 2000)
        }
      })
      .catch((err) => {
        setErrorMessage(err.response?.data?.message ?? 'Could not save the shared link')
        setStatus('error')
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm bg-white border border-slate-200 rounded-lg p-8 text-center">
        {status === 'saving' && (
          <>
            <Loader2 className="w-10 h-10 text-teal-600 mx-auto mb-4 animate-spin" />
            <p className="text-slate-700 font-medium">Saving to MemoryVault…</p>
          </>
        )}

        {status === 'success' && (
          <>
            <CheckCircle2 className="w-10 h-10 text-teal-600 mx-auto mb-4" />
            {item?.ogImageUrl && (
              <img src={item.ogImageUrl} alt="" className="w-full h-32 object-cover rounded-md mb-3" />
            )}
            <p className="text-slate-900 font-medium">{item?.title ?? item?.url ?? 'Saved'}</p>
            <p className="text-sm text-slate-500 mt-1">Taking you to your dashboard…</p>
          </>
        )}

        {status === 'error' && (
          <>
            <XCircle className="w-10 h-10 text-red-500 mx-auto mb-4" />
            <p className="text-slate-700 font-medium">{errorMessage}</p>
            <button
              onClick={() => navigate('/dashboard')}
              className="mt-4 text-sm text-teal-600 font-medium"
            >
              Go to dashboard
            </button>
          </>
        )}
      </div>
    </div>
  )
}
