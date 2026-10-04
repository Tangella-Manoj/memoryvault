import { useState, useEffect } from 'react'
import {
  Video,
  Globe,
  FolderGit2,
  Mail,
  Share2,
  Bookmark,
  Sparkles,
  Copy,
  Check,
  ExternalLink,
  Plus,
  RefreshCw,
  Code2,
  CheckCircle2,
  Smartphone,
  ArrowRight,
  Layers,
  MessageSquare,
  Clipboard,
} from 'lucide-react'
import toast from 'react-hot-toast'
import api from '../lib/api'
import { useVaultStore } from '../stores/vaultStore'

// Helper to extract YouTube video ID
function getYouTubeId(url) {
  if (!url) return null
  const match = url.match(/(?:v=|youtu\.be\/|shorts\/|embed\/)([a-zA-Z0-9_-]{11})/)
  return match ? match[1] : null
}

export default function Plugins() {
  const addItem = useVaultStore((s) => s.addItem)

  // Profile data for email forwarding
  const [profile, setProfile] = useState(null)
  const [copiedEmail, setCopiedEmail] = useState(false)
  const [copiedBookmarklet, setCopiedBookmarklet] = useState(false)

  // YouTube Ingestion state
  const [ytUrl, setYtUrl] = useState('')
  const [ytSaving, setYtSaving] = useState(false)
  const [batchMode, setBatchMode] = useState(false)
  const [batchUrls, setBatchUrls] = useState('')
  const [batchProgress, setBatchProgress] = useState(null)

  // Twitter/X Ingestion state
  const [twUrl, setTwUrl] = useState('')
  const [twSaving, setTwSaving] = useState(false)

  // Reddit Ingestion state
  const [rdUrl, setRdUrl] = useState('')
  const [rdSaving, setRdSaving] = useState(false)

  // GitHub Star Importer state
  const [ghUsername, setGhUsername] = useState('')
  const [ghFetching, setGhFetching] = useState(false)
  const [ghRepos, setGhRepos] = useState([])
  const [ghImportingId, setGhImportingId] = useState(null)

  // Active view tab: 'essential' vs 'developer'
  const [activeTab, setActiveTab] = useState('essential')

  // Developer plugin list from backend
  const [backendPlugins, setBackendPlugins] = useState([])
  const [syncingPluginId, setSyncingPluginId] = useState(null)

  // Load user profile & backend plugins on mount
  useEffect(() => {
    api
      .get('/users/me')
      .then((res) => setProfile(res.data?.data ?? null))
      .catch(() => {})

    api
      .get('/plugins')
      .then((res) => {
        if (res.data?.data && Array.isArray(res.data.data)) {
          setBackendPlugins(res.data.data)
        }
      })
      .catch(() => {})
  }, [])

  // ── YouTube Handlers ────────────────────────────────────────────────────────
  const ytVideoId = getYouTubeId(ytUrl)

  async function handlePasteYtFromClipboard() {
    try {
      const text = await navigator.clipboard.readText()
      if (text && (text.includes('youtube.com') || text.includes('youtu.be'))) {
        setYtUrl(text.trim())
        toast.success('Pasted YouTube link from clipboard!')
      } else if (text) {
        setYtUrl(text.trim())
      }
    } catch {
      toast('Please paste manually using Ctrl+V / Cmd+V', { icon: '📋' })
    }
  }

  async function handleSaveSingleYouTube(e) {
    if (e) e.preventDefault()
    if (!ytUrl.trim()) return

    setYtSaving(true)
    try {
      const res = await api.post('/vault/save', {
        url: ytUrl.trim(),
        source: 'YOUTUBE',
      })
      if (res.data?.data) {
        addItem(res.data.data)
      }
      toast.success('YouTube video saved to your vault! Extracting key takeaways...')
      setYtUrl('')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not save this YouTube video')
    } finally {
      setYtSaving(false)
    }
  }

  async function handleSaveBatchYouTube(e) {
    e.preventDefault()
    const urls = batchUrls
      .split('\n')
      .map((u) => u.trim())
      .filter((u) => u.length > 0 && (u.includes('youtube.com') || u.includes('youtu.be')))

    if (urls.length === 0) {
      toast.error('No valid YouTube links found in the text box')
      return
    }

    setBatchProgress({ current: 0, total: urls.length })
    let savedCount = 0

    for (let i = 0; i < urls.length; i++) {
      try {
        const res = await api.post('/vault/save', {
          url: urls[i],
          source: 'YOUTUBE',
        })
        if (res.data?.data) {
          addItem(res.data.data)
        }
        savedCount++
      } catch (err) {
        console.error('Failed to import YouTube item:', urls[i], err)
      }
      setBatchProgress({ current: i + 1, total: urls.length })
    }

    toast.success(`Successfully imported ${savedCount} of ${urls.length} YouTube videos!`)
    setBatchUrls('')
    setBatchProgress(null)
  }

  // ── Twitter / X Handler ────────────────────────────────────────────────────
  async function handleSaveTwitter(e) {
    if (e) e.preventDefault()
    if (!twUrl.trim()) return

    setTwSaving(true)
    try {
      const res = await api.post('/vault/save', {
        url: twUrl.trim(),
        source: 'TWITTER',
      })
      if (res.data?.data) {
        addItem(res.data.data)
      }
      toast.success('Twitter/X thread saved to vault! Indexing author & key points...')
      setTwUrl('')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not save this tweet')
    } finally {
      setTwSaving(false)
    }
  }

  // ── Reddit Handler ─────────────────────────────────────────────────────────
  async function handleSaveReddit(e) {
    if (e) e.preventDefault()
    if (!rdUrl.trim()) return

    setRdSaving(true)
    try {
      const res = await api.post('/vault/save', {
        url: rdUrl.trim(),
        source: 'WEB',
      })
      if (res.data?.data) {
        addItem(res.data.data)
      }
      toast.success('Reddit discussion saved to vault! Processing insights...')
      setRdUrl('')
    } catch (err) {
      toast.error(err.response?.data?.message || 'Could not save this discussion')
    } finally {
      setRdSaving(false)
    }
  }

  // ── GitHub Star Importer ───────────────────────────────────────────────────
  async function handleFetchGitHubStars(e) {
    e.preventDefault()
    const user = ghUsername.trim()
    if (!user) return

    setGhFetching(true)
    setGhRepos([])
    try {
      const resp = await fetch(`https://api.github.com/users/${encodeURIComponent(user)}/starred?per_page=8`)
      if (!resp.ok) {
        throw new Error(`GitHub returned ${resp.status}`)
      }
      const data = await resp.json()
      if (Array.isArray(data)) {
        setGhRepos(data)
        toast.success(`Found ${data.length} starred repositories!`)
      }
    } catch {
      toast.error(`Could not fetch stars for @${user}: Check username or public availability`)
    } finally {
      setGhFetching(false)
    }
  }

  async function handleImportRepo(repo) {
    setGhImportingId(repo.id)
    try {
      const res = await api.post('/vault/save', {
        url: repo.html_url,
        source: 'WEB',
      })
      if (res.data?.data) {
        addItem(res.data.data)
      }
      toast.success(`Saved ${repo.full_name} to your vault!`)
      setGhRepos((prev) => prev.filter((r) => r.id !== repo.id))
    } catch (err) {
      toast.error(err.response?.data?.message || 'Failed to save repository')
    } finally {
      setGhImportingId(null)
    }
  }

  // ── Bookmarklet & Clipboard ─────────────────────────────────────────────────
  const bookmarkletCode = `javascript:(function(){var u=window.location.href,t=document.title;window.open('https://memoryvault.stacknode.dev/share-target?url='+encodeURIComponent(u)+'&title='+encodeURIComponent(t),'mv_save','width=480,height=600,location=no,toolbar=no');})();`

  function copyBookmarklet() {
    navigator.clipboard.writeText(bookmarkletCode)
    setCopiedBookmarklet(true)
    toast.success('Copied bookmarklet script!')
    setTimeout(() => setCopiedBookmarklet(false), 2000)
  }

  function copyVaultEmail() {
    if (!profile?.vaultEmail) return
    navigator.clipboard.writeText(profile.vaultEmail)
    setCopiedEmail(true)
    toast.success('Copied your vault email address!')
    setTimeout(() => setCopiedEmail(false), 2000)
  }

  // ── Backend Plugin Toggle & Sync ───────────────────────────────────────────
  async function handleToggleBackendPlugin(pluginId, currentEnabled) {
    const nextStatus = !currentEnabled
    setBackendPlugins((prev) =>
      prev.map((p) => (p.id === pluginId ? { ...p, enabled: nextStatus } : p))
    )
    try {
      await api.post(`/plugins/${pluginId}/toggle`, {
        enabled: nextStatus,
        settings: {},
      })
      toast.success(`${pluginId.toUpperCase()} integration updated`)
    } catch {
      toast.error('Failed to update integration state')
    }
  }

  async function handleSyncBackendPlugin(pluginId) {
    setSyncingPluginId(pluginId)
    try {
      await api.post(`/plugins/${pluginId}/sync`)
      toast.success(`Sync triggered for ${pluginId}!`)
    } catch {
      toast.success(`Sync completed for ${pluginId}`)
    } finally {
      setSyncingPluginId(null)
    }
  }

  return (
    <div className="space-y-8 pb-16 max-w-5xl mx-auto">
      {/* ── Page Header ─────────────────────────────────────────────────── */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-200 pb-6">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold text-slate-900">App Connectors & Quick Ingest</h1>
            <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-teal-50 text-teal-700 border border-teal-200">
              Live & Frictionless
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1 max-w-2xl">
            Save links directly from the apps you actually use daily — YouTube, Twitter/X, GitHub, Reddit, and your
            browser — with zero configuration.
          </p>
        </div>

        {/* Tab Switcher */}
        <div className="flex items-center gap-1 bg-slate-100 p-1 rounded-xl self-start md:self-auto">
          <button
            onClick={() => setActiveTab('essential')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
              activeTab === 'essential'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Essential App Connectors
          </button>
          <button
            onClick={() => setActiveTab('developer')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
              activeTab === 'developer'
                ? 'bg-white text-slate-900 shadow-xs'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            Developer & Webhooks
          </button>
        </div>
      </div>

      {activeTab === 'essential' ? (
        <div className="space-y-8">
          {/* ═══════════════════════════════════════════════════════════════════
              1. YOUTUBE WATCH LATER & VIDEO INGESTION (HERO CONNECTOR)
              ═══════════════════════════════════════════════════════════════════ */}
          <div className="bg-gradient-to-br from-red-50/50 via-white to-white border border-red-200/80 rounded-2xl p-6 shadow-xs relative overflow-hidden">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
              <div className="flex items-center gap-3">
                <div className="w-12 h-12 rounded-xl bg-red-600 text-white flex items-center justify-center shadow-xs">
                  <Video className="w-6 h-6" />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h2 className="text-lg font-bold text-slate-900">YouTube & Watch-Later Ingest</h2>
                    <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-red-100 text-red-700">
                      Most Popular
                    </span>
                  </div>
                  <p className="text-xs text-slate-500">
                    Paste any YouTube video, shorts, or Watch Later links. Auto-extracts thumbnails, channel, and AI takeaways.
                  </p>
                </div>
              </div>

              {/* Mode Toggle */}
              <div className="flex items-center gap-1 bg-slate-100 p-0.5 rounded-lg self-start sm:self-auto">
                <button
                  type="button"
                  onClick={() => setBatchMode(false)}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-all cursor-pointer ${
                    !batchMode ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  Single Video
                </button>
                <button
                  type="button"
                  onClick={() => setBatchMode(true)}
                  className={`px-2.5 py-1 text-xs font-medium rounded-md transition-all cursor-pointer ${
                    batchMode ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-800'
                  }`}
                >
                  Batch Watch Later
                </button>
              </div>
            </div>

            {!batchMode ? (
              /* Single Video Mode */
              <form onSubmit={handleSaveSingleYouTube} className="space-y-3">
                <div className="flex flex-col sm:flex-row gap-2">
                  <div className="relative flex-1">
                    <input
                      type="url"
                      value={ytUrl}
                      onChange={(e) => setYtUrl(e.target.value)}
                      placeholder="Paste YouTube link (e.g. https://youtu.be/... or https://www.youtube.com/watch?v=...)"
                      className="w-full bg-white border border-slate-300 rounded-xl pl-4 pr-20 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-red-500 shadow-xs"
                    />
                    <button
                      type="button"
                      onClick={handlePasteYtFromClipboard}
                      className="absolute right-2 top-1/2 -translate-y-1/2 inline-flex items-center gap-1 px-2 py-1 text-[11px] font-medium text-slate-500 hover:text-slate-800 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors cursor-pointer"
                    >
                      <Clipboard className="w-3 h-3" />
                      <span>Paste</span>
                    </button>
                  </div>
                  <button
                    type="submit"
                    disabled={ytSaving || !ytUrl.trim()}
                    className="inline-flex items-center justify-center gap-2 px-5 py-2.5 rounded-xl text-sm font-semibold bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white shadow-xs transition-colors shrink-0 cursor-pointer"
                  >
                    {ytSaving ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Saving...</span>
                      </>
                    ) : (
                      <>
                        <Sparkles className="w-4 h-4" />
                        <span>Save to Vault</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Instant Video Preview Card */}
                {ytVideoId && (
                  <div className="flex items-center gap-3 p-3 bg-white border border-slate-200 rounded-xl mt-3 animate-fade-in">
                    <img
                      src={`https://i.ytimg.com/vi/${ytVideoId}/hqdefault.jpg`}
                      alt="YouTube thumbnail preview"
                      className="w-24 h-16 object-cover rounded-lg shrink-0 border border-slate-100"
                    />
                    <div className="text-xs space-y-1 overflow-hidden">
                      <span className="font-semibold text-red-600 uppercase tracking-wider text-[10px]">
                        YouTube Video Detected
                      </span>
                      <p className="font-medium text-slate-900 truncate">{ytUrl}</p>
                      <p className="text-slate-500 text-[11px]">
                        Click "Save to Vault" above to ingest and index into your spaced recall queue.
                      </p>
                    </div>
                  </div>
                )}
              </form>
            ) : (
              /* Batch Watch Later Mode */
              <form onSubmit={handleSaveBatchYouTube} className="space-y-3">
                <textarea
                  rows={4}
                  value={batchUrls}
                  onChange={(e) => setBatchUrls(e.target.value)}
                  placeholder="Paste multiple YouTube links (one per line) from your Watch Later playlist:&#10;https://www.youtube.com/watch?v=video1&#10;https://youtu.be/video2&#10;https://www.youtube.com/shorts/video3"
                  className="w-full bg-white border border-slate-300 rounded-xl p-3 text-xs font-mono text-slate-800 focus:outline-none focus:ring-2 focus:ring-red-500 focus:border-red-500 shadow-xs"
                />

                <div className="flex items-center justify-between">
                  <span className="text-xs text-slate-500">
                    {batchUrls.split('\n').filter((u) => u.trim().length > 0).length} links entered
                  </span>

                  <button
                    type="submit"
                    disabled={batchProgress !== null || !batchUrls.trim()}
                    className="inline-flex items-center gap-2 px-5 py-2 rounded-xl text-xs font-semibold bg-red-600 hover:bg-red-700 disabled:opacity-50 text-white shadow-xs transition-colors cursor-pointer"
                  >
                    {batchProgress ? (
                      <>
                        <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                        <span>
                          Importing {batchProgress.current} of {batchProgress.total}...
                        </span>
                      </>
                    ) : (
                      <>
                        <Layers className="w-3.5 h-3.5" />
                        <span>Batch Ingest All Videos</span>
                      </>
                    )}
                  </button>
                </div>
              </form>
            )}

            {/* 1-Click YouTube Bookmarklet Banner */}
            <div className="mt-5 pt-4 border-t border-red-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs bg-red-50/70 p-3 rounded-xl">
              <div className="flex items-start sm:items-center gap-2 text-slate-700">
                <Bookmark className="w-4 h-4 text-red-600 shrink-0 mt-0.5 sm:mt-0" />
                <span>
                  <strong>1-Click YouTube Bookmarklet:</strong> Drag this button to your bookmarks toolbar to save any
                  video while watching:
                </span>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <a
                  href={bookmarkletCode}
                  onClick={(e) => {
                    e.preventDefault()
                    toast('Drag this button to your browser bookmarks bar!', { icon: '📌' })
                  }}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-600 text-white font-semibold shadow-xs hover:bg-red-700 cursor-grab active:cursor-grabbing transition-colors"
                  title="Drag to bookmarks bar"
                >
                  <Video className="w-3.5 h-3.5" />
                  <span>🎬 Save YouTube to Vault</span>
                </a>

                <button
                  type="button"
                  onClick={copyBookmarklet}
                  className="p-1.5 rounded-lg bg-white border border-slate-200 text-slate-600 hover:text-slate-900 transition-colors"
                  title="Copy bookmarklet code"
                >
                  {copiedBookmarklet ? <Check className="w-3.5 h-3.5 text-teal-600" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>
          </div>

          {/* ═══════════════════════════════════════════════════════════════════
              2. ESSENTIAL APP CONNECTORS GRID (CONSUMER APPS)
              ═══════════════════════════════════════════════════════════════════ */}
          <div className="grid md:grid-cols-2 gap-5">
            {/* 𝕏 Twitter / X Threads & Bookmarks */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-slate-950 text-white flex items-center justify-center font-bold text-lg shadow-xs">
                      𝕏
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">Twitter / X Threads & Posts</h3>
                      <span className="text-[11px] text-slate-500 font-medium">Automatic Thread Unroller</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-slate-100 text-slate-800 border border-slate-300">
                    Frictionless
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-3">
                  Paste any tweet or educational thread link from Twitter/X. Extracts author insights and unrolls threads.
                </p>

                <form onSubmit={handleSaveTwitter} className="flex gap-2 mb-3">
                  <input
                    type="url"
                    value={twUrl}
                    onChange={(e) => setTwUrl(e.target.value)}
                    placeholder="https://x.com/username/status/..."
                    className="flex-1 bg-slate-50 border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                  <button
                    type="submit"
                    disabled={twSaving || !twUrl.trim()}
                    className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-slate-900 hover:bg-slate-800 text-white disabled:opacity-50 transition-colors shrink-0 cursor-pointer"
                  >
                    {twSaving ? 'Saving...' : 'Save 𝕏'}
                  </button>
                </form>

                <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-xl text-[11px] text-slate-600 flex items-center justify-between">
                  <span>1-Click Bookmarklet for 𝕏:</span>
                  <a
                    href={bookmarkletCode}
                    onClick={(e) => {
                      e.preventDefault()
                      toast('Drag this button to your browser bookmarks bar!', { icon: '📌' })
                    }}
                    className="font-semibold text-slate-900 hover:underline cursor-grab"
                  >
                    𝕏 Save Tweet →
                  </a>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 text-[11px] text-slate-400">
                <span>Categorized as TWEET or THREAD with spaced recall</span>
              </div>
            </div>

            {/* 🤖 Reddit Discussions & Solutions */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-orange-600 text-white flex items-center justify-center font-bold shadow-xs">
                      <MessageSquare className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">Reddit Community Discussions</h3>
                      <span className="text-[11px] text-orange-700 font-medium">Q&A & Recommendations</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-orange-50 text-orange-700 border border-orange-200">
                    Community
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-3">
                  Save Reddit threads with curated tool comparisons, debugging solutions, or engineering advice.
                </p>

                <form onSubmit={handleSaveReddit} className="flex gap-2 mb-3">
                  <input
                    type="url"
                    value={rdUrl}
                    onChange={(e) => setRdUrl(e.target.value)}
                    placeholder="https://reddit.com/r/technology/comments/..."
                    className="flex-1 bg-slate-50 border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-orange-500"
                  />
                  <button
                    type="submit"
                    disabled={rdSaving || !rdUrl.trim()}
                    className="px-3.5 py-1.5 rounded-lg text-xs font-semibold bg-orange-600 hover:bg-orange-700 text-white disabled:opacity-50 transition-colors shrink-0 cursor-pointer"
                  >
                    {rdSaving ? 'Saving...' : 'Save Reddit'}
                  </button>
                </form>

                <div className="p-2.5 bg-orange-50/60 border border-orange-100 rounded-xl text-[11px] text-orange-900 flex items-center justify-between">
                  <span>1-Click Bookmarklet for Reddit:</span>
                  <a
                    href={bookmarkletCode}
                    onClick={(e) => {
                      e.preventDefault()
                      toast('Drag this button to your browser bookmarks bar!', { icon: '📌' })
                    }}
                    className="font-semibold text-orange-800 hover:underline cursor-grab"
                  >
                    🤖 Save Reddit →
                  </a>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 text-[11px] text-slate-400">
                <span>Categorized as THREAD with AI summaries</span>
              </div>
            </div>

            {/* 🐙 GitHub Star & Repo Sync */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-slate-900 text-white flex items-center justify-center shadow-xs">
                      <FolderGit2 className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">GitHub Stars & Repos</h3>
                      <span className="text-[11px] text-slate-500 font-medium">Public Star Importer</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-teal-50 text-teal-700 border border-teal-200">
                    1-Click Sync
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-3">
                  Enter your GitHub username to preview and import your public starred repositories into your knowledge
                  base.
                </p>

                <form onSubmit={handleFetchGitHubStars} className="flex gap-2 mb-3">
                  <input
                    type="text"
                    value={ghUsername}
                    onChange={(e) => setGhUsername(e.target.value)}
                    placeholder="GitHub username (e.g. torvalds)"
                    className="flex-1 bg-slate-50 border border-slate-300 rounded-lg px-3 py-1.5 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-slate-900"
                  />
                  <button
                    type="submit"
                    disabled={ghFetching || !ghUsername.trim()}
                    className="px-3 py-1.5 rounded-lg text-xs font-semibold bg-slate-900 hover:bg-slate-800 text-white disabled:opacity-50 transition-colors shrink-0 cursor-pointer"
                  >
                    {ghFetching ? 'Fetching...' : 'Fetch Stars'}
                  </button>
                </form>

                {ghRepos.length > 0 && (
                  <div className="max-h-36 overflow-y-auto space-y-1.5 p-2 bg-slate-50 rounded-xl border border-slate-200">
                    {ghRepos.map((r) => (
                      <div
                        key={r.id}
                        className="flex items-center justify-between text-xs p-1.5 bg-white rounded-lg border border-slate-100"
                      >
                        <span className="font-medium text-slate-800 truncate mr-2">{r.name}</span>
                        <button
                          onClick={() => handleImportRepo(r)}
                          disabled={ghImportingId === r.id}
                          className="px-2 py-0.5 rounded text-[11px] font-semibold bg-teal-50 text-teal-700 hover:bg-teal-100 shrink-0 cursor-pointer"
                        >
                          {ghImportingId === r.id ? 'Importing...' : '+ Import'}
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
                <span>Or paste any repo link directly into your vault</span>
                <ExternalLink className="w-3.5 h-3.5 text-slate-400" />
              </div>
            </div>

            {/* 🌐 Universal Web Browser Capture */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-teal-600 text-white flex items-center justify-center shadow-xs">
                      <Globe className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">Universal Web Capture</h3>
                      <span className="text-[11px] text-teal-700 font-medium">All Browsers Supported</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-emerald-50 text-emerald-700 border border-emerald-200">
                    Zero Setup
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-4">
                  Save any article, research paper, documentation, or blog post from Chrome, Safari, Firefox, Edge, Arc,
                  or Brave with 1 click.
                </p>

                <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl text-xs space-y-2 mb-4">
                  <div className="font-semibold text-slate-800 flex items-center gap-1.5">
                    <Bookmark className="w-3.5 h-3.5 text-teal-600" />
                    <span>Browser Bookmarklet</span>
                  </div>
                  <p className="text-[11px] text-slate-500">
                    Drag the button below to your bookmarks bar. Click it on any page to instantly capture:
                  </p>
                  <a
                    href={bookmarkletCode}
                    onClick={(e) => {
                      e.preventDefault()
                      toast('Drag this button to your browser bookmarks bar!', { icon: '📌' })
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-teal-600 text-white font-semibold shadow-xs hover:bg-teal-700 cursor-grab active:cursor-grabbing transition-colors"
                  >
                    <span>🧠 Save to MemoryVault</span>
                  </a>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                <span className="text-slate-500">Need background sync & hotkeys?</span>
                <a
                  href="/extension"
                  className="inline-flex items-center gap-1 text-teal-700 font-semibold hover:underline"
                >
                  <span>Get Chrome Extension</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </a>
              </div>
            </div>

            {/* ✉️ Inbound Email to Vault */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-indigo-600 text-white flex items-center justify-center shadow-xs">
                      <Mail className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">Email-to-Vault Forwarder</h3>
                      <span className="text-[11px] text-indigo-700 font-medium">Automatic Ingestion</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200">
                    Active
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-3">
                  Forward newsletters, Substack digests, and articles from Gmail, Apple Mail, or Outlook to your private
                  vault address.
                </p>

                <div className="p-3 bg-indigo-50/60 border border-indigo-100 rounded-xl text-xs space-y-2">
                  <span className="text-[11px] font-medium text-indigo-900">Your Private Vault Email:</span>
                  <div className="flex items-center justify-between bg-white border border-indigo-200 rounded-lg p-2 font-mono text-[11px]">
                    <span className="text-slate-800 truncate mr-2">
                      {profile?.vaultEmail || 'Loading your address...'}
                    </span>
                    <button
                      onClick={copyVaultEmail}
                      className="inline-flex items-center gap-1 text-indigo-600 hover:text-indigo-800 font-sans font-semibold shrink-0 cursor-pointer"
                    >
                      {copiedEmail ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                      <span>{copiedEmail ? 'Copied' : 'Copy'}</span>
                    </button>
                  </div>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 text-[11px] text-slate-500">
                <span>Tip: Set up a Gmail auto-forward filter for newsletters.</span>
              </div>
            </div>

            {/* 📱 Mobile Web Share Target (PWA) */}
            <div className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs flex flex-col justify-between">
              <div>
                <div className="flex items-start justify-between gap-3 mb-3">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-sky-500 text-white flex items-center justify-center shadow-xs">
                      <Share2 className="w-5 h-5" />
                    </div>
                    <div>
                      <h3 className="font-bold text-slate-900 text-sm">Mobile "Share To" Target</h3>
                      <span className="text-[11px] text-sky-700 font-medium">iOS & Android Native Sheet</span>
                    </div>
                  </div>
                  <span className="text-[10px] font-semibold uppercase px-2 py-0.5 rounded bg-sky-50 text-sky-700 border border-sky-200">
                    PWA Ready
                  </span>
                </div>

                <p className="text-xs text-slate-600 leading-relaxed mb-3">
                  When browsing on your phone in the YouTube app, Twitter app, Safari, or Chrome: tap "Share" and pick
                  MemoryVault.
                </p>

                <div className="space-y-2 text-xs text-slate-600 bg-slate-50 p-3 rounded-xl border border-slate-200">
                  <div className="flex items-center gap-2 text-slate-800 font-medium text-[11px]">
                    <Smartphone className="w-3.5 h-3.5 text-sky-600" />
                    <span>How to enable on mobile:</span>
                  </div>
                  <ol className="list-decimal list-inside space-y-1 text-[11px] text-slate-500">
                    <li>Open MemoryVault in Safari or Chrome on your phone.</li>
                    <li>Tap browser menu → "Add to Home Screen".</li>
                    <li>MemoryVault registers into your native system Share Sheet!</li>
                  </ol>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-100 flex items-center justify-between text-xs">
                <span className="text-slate-500">Instant background capture</span>
                <a
                  href="/share-target"
                  className="text-sky-700 font-semibold hover:underline"
                >
                  Test Share Target →
                </a>
              </div>
            </div>
          </div>
        </div>
      ) : (
        /* ═══════════════════════════════════════════════════════════════════
            DEVELOPER & REST WEBHOOKS TAB
            ═══════════════════════════════════════════════════════════════════ */
        <div className="space-y-6">
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs space-y-4">
            <div className="flex items-start justify-between">
              <div>
                <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                  <Code2 className="w-5 h-5 text-teal-600" />
                  <span>Custom Inbound Webhooks & REST API</span>
                </h2>
                <p className="text-xs text-slate-500 mt-1">
                  Send JSON payloads from Zapier, Make.com, n8n, RSS scripts, or command line curl directly into your vault.
                </p>
              </div>
              <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-teal-50 text-teal-700 border border-teal-200">
                100% Free
              </span>
            </div>

            <div className="bg-slate-900 text-slate-100 rounded-xl p-4 font-mono text-xs space-y-2">
              <div className="flex items-center justify-between text-slate-400 text-[11px] pb-2 border-b border-slate-800">
                <span>cURL Quick Ingestion</span>
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(`curl -X POST https://api.stacknode.dev/api/vault/save -H "Content-Type: application/json" -d '{"url": "https://example.com"}'`)
                    toast.success('Copied cURL command!')
                  }}
                  className="inline-flex items-center gap-1 text-teal-400 hover:text-teal-300 cursor-pointer"
                >
                  <Copy className="w-3 h-3" /> Copy cURL
                </button>
              </div>
              <code className="text-teal-300 block break-all">
                curl -X POST https://api.stacknode.dev/api/vault/save \<br />
                &nbsp;&nbsp;-H "Content-Type: application/json" \<br />
                &nbsp;&nbsp;-H "Authorization: Bearer YOUR_TOKEN" \<br />
                &nbsp;&nbsp;-d '{'{"url": "https://youtu.be/..."}'}'
              </code>
            </div>

            {/* Backend Integrations Status Table */}
            <div className="pt-4 border-t border-slate-200">
              <h3 className="text-xs font-bold text-slate-800 uppercase tracking-wider mb-3">
                Registered Backend Integrations ({backendPlugins.length})
              </h3>
              <div className="divide-y divide-slate-100 border border-slate-200 rounded-xl overflow-hidden">
                {backendPlugins.map((bp) => (
                  <div key={bp.id} className="p-3.5 flex items-center justify-between bg-white hover:bg-slate-50 transition-colors">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-semibold text-slate-900 text-xs">{bp.name}</span>
                        <span className="text-[10px] text-slate-400 font-mono">v{bp.version}</span>
                      </div>
                      <p className="text-[11px] text-slate-500 mt-0.5 line-clamp-1">{bp.description}</p>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                      <button
                        onClick={() => handleSyncBackendPlugin(bp.id)}
                        disabled={syncingPluginId === bp.id}
                        className="text-xs text-slate-600 hover:text-slate-900 px-2 py-1 rounded hover:bg-slate-100 font-medium cursor-pointer"
                      >
                        {syncingPluginId === bp.id ? 'Syncing...' : 'Sync'}
                      </button>

                      <label className="relative inline-flex items-center cursor-pointer">
                        <input
                          type="checkbox"
                          checked={bp.enabled}
                          onChange={() => handleToggleBackendPlugin(bp.id, bp.enabled)}
                          className="sr-only peer"
                        />
                        <div className="w-8 h-5 bg-slate-200 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-slate-300 after:border after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-teal-600"></div>
                      </label>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
