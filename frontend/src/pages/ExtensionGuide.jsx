import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  Brain,
  Download,
  CheckCircle2,
  Copy,
  ExternalLink,
  ShieldCheck,
  Zap,
  Search,
  Bookmark,
  ArrowRight,
  FolderArchive,
  ToggleRight,
  MousePointerClick,
  Sparkles,
} from 'lucide-react'
import toast from 'react-hot-toast'

export default function ExtensionGuide() {
  const [copied, setCopied] = useState(false)

  function copyChromeUrl() {
    navigator.clipboard.writeText('chrome://extensions')
    setCopied(true)
    toast.success('Copied "chrome://extensions" to clipboard!')
    setTimeout(() => setCopied(false), 2500)
  }

  const steps = [
    {
      number: '1',
      title: 'Download & Extract the Extension',
      icon: FolderArchive,
      desc: 'Click the button above to download the packaged extension (.zip). Once downloaded, extract (unzip) the file into a folder on your computer (e.g., in your Documents or Downloads).',
      tip: 'Do not delete the extracted folder after installation, as Chrome loads the extension directly from it.',
    },
    {
      number: '2',
      title: 'Open Extensions Management',
      icon: ExternalLink,
      desc: (
        <span>
          Open Google Chrome (or any Chromium browser like Brave or Edge) and navigate to{' '}
          <code className="bg-slate-100 text-teal-800 px-2 py-0.5 rounded font-mono text-xs">
            chrome://extensions
          </code>{' '}
          in the address bar.
        </span>
      ),
      action: (
        <button
          onClick={copyChromeUrl}
          className="mt-2.5 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium bg-slate-100 text-slate-700 hover:bg-slate-200 transition-colors"
        >
          <Copy className="w-3.5 h-3.5" />
          {copied ? 'Copied to clipboard!' : 'Copy "chrome://extensions"'}
        </button>
      ),
    },
    {
      number: '3',
      title: 'Turn On Developer Mode',
      icon: ToggleRight,
      desc: 'Look at the top-right corner of the Extensions page. Toggle the "Developer mode" switch to ON.',
      tip: 'This enables Chrome to load local unpacked extension packages safely.',
    },
    {
      number: '4',
      title: 'Click "Load Unpacked"',
      icon: MousePointerClick,
      desc: 'Click the "Load unpacked" button that appears in the top-left toolbar. In the file picker, select the unzipped MemoryVault extension folder you extracted in Step 1.',
      tip: 'Select the folder containing "manifest.json", not the outer zip file.',
    },
    {
      number: '5',
      title: 'Pin & Log In',
      icon: Bookmark,
      desc: 'Click the puzzle icon (🧩) in your Chrome toolbar and pin MemoryVault. Click the MemoryVault icon, log in with your email & password, and you are ready to capture knowledge instantly!',
    },
  ]

  const features = [
    {
      icon: Zap,
      title: '1-Click Instant Save',
      desc: 'Floating quick-save button appears on every page you read. Save any article, blog, or discussion in 1 second.',
    },
    {
      icon: Search,
      title: 'Search Result Overlays',
      desc: 'Search DuckDuckGo or Google and MemoryVault automatically surfaces relevant articles you saved in the past.',
    },
    {
      icon: ShieldCheck,
      title: 'Zero Tracking & Private',
      desc: 'Runs entirely on your terms. Manifest V3 compliant, open-source, and never sells or tracks your browsing habits.',
    },
  ]

  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900">
      {/* Top Navbar */}
      <header className="border-b border-slate-200 bg-white/80 backdrop-blur sticky top-0 z-30">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <Link to="/login" className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-teal-600 to-teal-400 flex items-center justify-center text-white shadow-sm">
              <Brain className="w-4 h-4" />
            </div>
            <span className="font-bold text-slate-900 text-lg">MemoryVault</span>
          </Link>

          <div className="flex items-center gap-3">
            <Link
              to="/login"
              className="text-sm font-medium text-slate-600 hover:text-slate-900 px-3 py-1.5"
            >
              Sign in
            </Link>
            <Link
              to="/register"
              className="text-sm font-medium text-white bg-teal-600 hover:bg-teal-700 px-4 py-1.5 rounded-lg shadow-xs transition-colors"
            >
              Get started free
            </Link>
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <section className="relative overflow-hidden pt-12 pb-16 px-6 sm:px-8 border-b border-slate-200/80 bg-gradient-to-b from-teal-50/40 via-white to-slate-50">
        <div className="max-w-4xl mx-auto text-center">
          <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-teal-100/80 text-teal-800 text-xs font-semibold uppercase tracking-wider mb-5">
            <Sparkles className="w-3.5 h-3.5 text-teal-600" />
            Chrome Extension v1.0 • Free for All Users
          </div>

          <h1 className="text-3xl sm:text-5xl font-extrabold text-slate-950 tracking-tight leading-tight">
            Supercharge Your Memory with the{' '}
            <span className="text-teal-600">MemoryVault Extension</span>
          </h1>

          <p className="mt-5 text-base sm:text-lg text-slate-600 max-w-2xl mx-auto leading-relaxed">
            Capture links while you read, automatically rediscover your saved knowledge inside
            Google & DuckDuckGo, and never lose an important bookmark again.
          </p>

          {/* Primary Download CTA */}
          <div className="mt-8 flex flex-col sm:flex-row items-center justify-center gap-4">
            <a
              href="/MemoryVault-Extension.zip"
              download="MemoryVault-Extension.zip"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2.5 px-6 py-3.5 rounded-xl font-semibold text-white bg-teal-600 hover:bg-teal-700 shadow-lg shadow-teal-600/25 transition-all transform hover:-translate-y-0.5 active:translate-y-0 text-base"
            >
              <Download className="w-5 h-5" />
              <span>Download Extension (.zip)</span>
            </a>

            <a
              href="#instructions"
              className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-5 py-3.5 rounded-xl font-medium text-slate-700 bg-white hover:bg-slate-100 border border-slate-200 transition-colors text-sm"
            >
              <span>View Installation Steps</span>
              <ArrowRight className="w-4 h-4 text-slate-400" />
            </a>
          </div>

          <div className="mt-4 flex items-center justify-center gap-4 text-xs text-slate-500">
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-teal-600" />
              Manifest V3 Ready
            </span>
            <span>•</span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-teal-600" />
              Chromium Browsers (Chrome, Brave, Edge)
            </span>
            <span>•</span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-teal-600" />
              ~10 KB Lightweight
            </span>
          </div>
        </div>
      </section>

      {/* Feature Highlights */}
      <section className="py-12 px-6 max-w-5xl mx-auto">
        <div className="grid md:grid-cols-3 gap-6">
          {features.map((feat) => {
            const Icon = feat.icon
            return (
              <div
                key={feat.title}
                className="bg-white p-6 rounded-xl border border-slate-200 shadow-xs hover:shadow-md transition-shadow"
              >
                <div className="w-10 h-10 rounded-lg bg-teal-50 text-teal-600 flex items-center justify-center mb-4">
                  <Icon className="w-5 h-5" />
                </div>
                <h2 className="font-semibold text-slate-900 text-base mb-1.5">{feat.title}</h2>
                <p className="text-sm text-slate-600 leading-relaxed">{feat.desc}</p>
              </div>
            )
          })}
        </div>
      </section>

      {/* Step-by-Step Installation Guide */}
      <section id="instructions" className="py-12 px-6 max-w-3xl mx-auto w-full">
        <div className="text-center mb-10">
          <h2 className="text-2xl sm:text-3xl font-bold text-slate-900">
            How to Install the Extension in 1 Minute
          </h2>
          <p className="text-sm text-slate-500 mt-2">
            No Chrome Web Store account needed. Follow these 5 quick steps:
          </p>
        </div>

        <div className="space-y-6">
          {steps.map((s) => {
            const Icon = s.icon
            return (
              <div
                key={s.number}
                className="bg-white border border-slate-200 rounded-2xl p-5 sm:p-6 shadow-xs flex gap-4 sm:gap-6 items-start"
              >
                <div className="shrink-0 w-10 h-10 rounded-full bg-teal-600 text-white font-bold flex items-center justify-center text-sm shadow-sm">
                  {s.number}
                </div>

                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Icon className="w-4 h-4 text-teal-600" />
                    <h3 className="font-bold text-slate-900 text-base">{s.title}</h3>
                  </div>

                  <div className="text-sm text-slate-600 leading-relaxed">{s.desc}</div>

                  {s.action && <div>{s.action}</div>}

                  {s.tip && (
                    <div className="mt-3 text-xs bg-slate-50 border border-slate-200/80 rounded-lg px-3 py-2 text-slate-600 flex items-start gap-1.5">
                      <span className="font-semibold text-teal-700 shrink-0">Note:</span>
                      <span>{s.tip}</span>
                    </div>
                  )}
                </div>
              </div>
            )
          })}
        </div>

        {/* Bottom CTA Box */}
        <div className="mt-12 bg-gradient-to-r from-teal-900 to-slate-900 text-white rounded-2xl p-8 text-center shadow-xl">
          <Brain className="w-10 h-10 text-teal-400 mx-auto mb-3" />
          <h3 className="text-xl font-bold">Ready to Start Building Your Second Brain?</h3>
          <p className="text-slate-300 text-sm mt-2 max-w-md mx-auto">
            Log in to the web app or create a free account to sync your links across devices.
          </p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-3">
            <Link
              to="/register"
              className="px-5 py-2.5 rounded-lg bg-teal-500 hover:bg-teal-400 text-slate-950 font-semibold text-sm transition-colors"
            >
              Create Free Account
            </Link>
            <Link
              to="/login"
              className="px-5 py-2.5 rounded-lg bg-white/10 hover:bg-white/20 text-white font-medium text-sm transition-colors"
            >
              Sign In to Web App
            </Link>
            <a
              href="/MemoryVault-Extension.zip"
              download="MemoryVault-Extension.zip"
              className="px-5 py-2.5 rounded-lg border border-teal-400/40 text-teal-200 hover:bg-teal-500/10 font-medium text-sm transition-colors flex items-center gap-1.5"
            >
              <Download className="w-4 h-4" />
              Download .zip
            </a>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="mt-auto border-t border-slate-200 py-6 text-center text-xs text-slate-500">
        <p>© {new Date().getFullYear()} MemoryVault. Built for intelligent link saving and proactive rediscovery.</p>
      </footer>
    </div>
  )
}
