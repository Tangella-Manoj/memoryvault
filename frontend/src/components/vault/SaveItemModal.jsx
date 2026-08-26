import { useState } from 'react'
import { X, Check } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../../lib/api'
import { useUiStore } from '../../stores/uiStore'
import { useVaultStore } from '../../stores/vaultStore'

export default function SaveItemModal() {
  const open = useUiStore((s) => s.saveModalOpen)
  const close = useUiStore((s) => s.closeSaveModal)
  const addItem = useVaultStore((s) => s.addItem)
  const [url, setUrl] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  if (!open) return null

  async function handleSave(e) {
    e.preventDefault()
    setSaving(true)
    try {
      const response = await api.post('/vault/save', { url })
      addItem(response.data.data)
      setSaved(true)
      setTimeout(() => {
        setSaved(false)
        setUrl('')
        close()
      }, 900)
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Could not save that link')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-slate-900/40 flex items-center justify-center z-50 px-4">
      <div className="bg-white rounded-lg w-full max-w-md p-6 relative">
        <button onClick={close} className="absolute top-4 right-4 text-slate-400 hover:text-slate-600">
          <X className="w-5 h-5" />
        </button>

        <h2 className="text-lg font-semibold text-slate-900 mb-4">Save to your vault</h2>

        {saved ? (
          <div className="flex flex-col items-center py-8 text-teal-600">
            <Check className="w-10 h-10 mb-2" />
            <p className="text-sm font-medium">Saved — we'll enrich it in the background</p>
          </div>
        ) : (
          <form onSubmit={handleSave} className="space-y-4">
            <input
              autoFocus
              type="url"
              required
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://…"
              className="w-full border border-slate-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
            <button
              type="submit"
              disabled={saving}
              className="w-full bg-teal-600 text-white rounded-md py-2 text-sm font-medium hover:bg-teal-700 disabled:opacity-50"
            >
              {saving ? 'Saving…' : 'Save'}
            </button>
          </form>
        )}
      </div>
    </div>
  )
}
