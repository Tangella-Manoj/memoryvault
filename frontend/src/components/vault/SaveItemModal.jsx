import { useState } from 'react'
import { X, Check, Clipboard, Sparkles, Video, Globe } from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../../lib/api'
import { useUiStore } from '../../stores/uiStore'
import { useVaultStore } from '../../stores/vaultStore'

function getPlatformDetails(url) {
  if (!url) return null
  const lower = url.toLowerCase()
  if (lower.includes('youtube.com') || lower.includes('youtu.be')) {
    const match = url.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)
    return {
      name: 'YouTube Video',
      icon: Video,
      color: 'bg-red-50 text-red-700 border-red-200',
      badge: 'YouTube',
      videoId: match ? match[1] : null,
      source: 'YOUTUBE',
    }
  }
  if (lower.includes('twitter.com') || lower.includes('x.com')) {
    return {
      name: 'Twitter / X Thread',
      color: 'bg-slate-900 text-white border-slate-700',
      badge: 'Twitter / X',
      source: 'TWITTER',
    }
  }
  if (lower.includes('github.com')) {
    return {
      name: 'GitHub Repository',
      color: 'bg-slate-100 text-slate-800 border-slate-300',
      badge: 'GitHub',
      source: 'WEB',
    }
  }
  if (lower.includes('reddit.com')) {
    return {
      name: 'Reddit Discussion',
      color: 'bg-orange-50 text-orange-700 border-orange-200',
      badge: 'Reddit',
      source: 'WEB',
    }
  }
  if (lower.includes('medium.com') || lower.includes('substack.com')) {
    return {
      name: 'Newsletter / Article',
      color: 'bg-blue-50 text-blue-700 border-blue-200',
      badge: 'Article',
      source: 'WEB',
    }
  }
  return {
    name: 'Web Link',
    color: 'bg-slate-50 text-slate-700 border-slate-200',
    badge: 'Web',
    source: 'WEB',
  }
}

export default function SaveItemModal() {
  const open = useUiStore((s) => s.saveModalOpen)
  const close = useUiStore((s) => s.closeSaveModal)
  const addItem = useVaultStore((s) => s.addItem)
  const [url, setUrl] = useState('')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  if (!open) return null

  const platform = getPlatformDetails(url)

  async function handlePasteFromClipboard() {
    try {
      const text = await navigator.clipboard.readText()
      if (text && (text.startsWith('http://') || text.startsWith('https://'))) {
        setUrl(text.trim())
        toast.success('Pasted link from clipboard!')
      } else if (text) {
        setUrl(text.trim())
      } else {
        toast('Clipboard is empty', { icon: '📋' })
      }
    } catch {
      toast('Please paste manually using Ctrl+V / Cmd+V', { icon: '📋' })
    }
  }

  async function handleSave(e) {
    e.preventDefault()
    if (!url.trim()) return

    setSaving(true)
    try {
      const response = await api.post('/vault/save', {
        url: url.trim(),
        source: platform?.source || 'WEB',
      })
      addItem(response.data.data)
      setSaved(true)
      setTimeout(() => {
        setSaved(false)
        setUrl('')
        close()
      }, 1000)
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Could not save that link')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-slate-900/50 backdrop-blur-xs flex items-center justify-center z-50 px-4">
      <div className="bg-white rounded-2xl w-full max-w-lg p-6 relative shadow-2xl border border-slate-100 animate-scale-up">
        <button
          onClick={close}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1 rounded-lg hover:bg-slate-100 transition-colors cursor-pointer"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-2 mb-1">
          <Sparkles className="w-5 h-5 text-teal-600" />
          <h2 className="text-lg font-bold text-slate-900">Quick Save to Vault</h2>
        </div>
        <p className="text-xs text-slate-500 mb-4">
          Paste any copied link from YouTube, Twitter, GitHub, Reddit, or your browser.
        </p>

        {saved ? (
          <div className="flex flex-col items-center py-8 text-teal-600 animate-fade-in">
            <Check className="w-12 h-12 mb-2 bg-teal-50 rounded-full p-2" />
            <p className="text-sm font-semibold text-slate-900">Saved to your Second Brain!</p>
            <p className="text-xs text-slate-500 mt-1">Extracting takeaways & scheduling spaced recall...</p>
          </div>
        ) : (
          <form onSubmit={handleSave} className="space-y-4">
            <div className="relative">
              <input
                autoFocus
                type="url"
                required
                value={url}
                onChange={(e) => setUrl(e.target.value)}
                placeholder="https://youtu.be/... or https://twitter.com/..."
                className="w-full border border-slate-300 rounded-xl pl-3.5 pr-24 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 shadow-xs"
              />
              <button
                type="button"
                onClick={handlePasteFromClipboard}
                className="absolute right-2 top-1/2 -translate-y-1/2 inline-flex items-center gap-1 px-2.5 py-1 text-xs font-medium text-slate-600 hover:text-slate-900 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
                title="Paste from clipboard"
              >
                <Clipboard className="w-3 h-3" />
                <span>Paste</span>
              </button>
            </div>

            {/* Live Detected Platform Preview */}
            {url.trim().length > 8 && platform && (
              <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl space-y-2 animate-fade-in text-xs">
                <div className="flex items-center justify-between">
                  <span className={`px-2 py-0.5 rounded-md font-semibold text-[11px] border ${platform.color}`}>
                    {platform.badge} Detected
                  </span>
                  <span className="text-[11px] text-slate-500">Auto-categorized</span>
                </div>

                {platform.videoId && (
                  <div className="flex items-center gap-2.5 pt-1">
                    <img
                      src={`https://i.ytimg.com/vi/${platform.videoId}/hqdefault.jpg`}
                      alt="Thumbnail preview"
                      className="w-20 h-12 object-cover rounded-md border border-slate-200"
                    />
                    <span className="text-[11px] text-slate-600 line-clamp-2">
                      Ready to fetch video title, channel, duration, and AI insights.
                    </span>
                  </div>
                )}
              </div>
            )}

            <button
              type="submit"
              disabled={saving || !url.trim()}
              className="w-full bg-teal-600 text-white rounded-xl py-2.5 text-sm font-semibold hover:bg-teal-700 disabled:opacity-50 transition-colors shadow-xs cursor-pointer flex items-center justify-center gap-2"
            >
              {saving ? (
                <span>Ingesting & Saving...</span>
              ) : (
                <>
                  <Sparkles className="w-4 h-4" />
                  <span>Save to Vault</span>
                </>
              )}
            </button>

            {/* Quick App Badges / Hints */}
            <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
              <span>Supports YouTube, Twitter/X, GitHub, Reddit, Articles</span>
              <a href="/plugins" onClick={close} className="text-teal-600 hover:underline">
                View Connectors →
              </a>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
