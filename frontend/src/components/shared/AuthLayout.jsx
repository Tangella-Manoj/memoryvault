import { Link } from 'react-router-dom'
import { Brain, Download, Sparkles } from 'lucide-react'

export default function AuthLayout({ children, title, subtitle }) {
  return (
    <div className="min-h-screen flex flex-col bg-slate-50 text-slate-900 relative overflow-hidden">
      {/* Ambient gradient glows */}
      <div
        className="pointer-events-none absolute -top-40 left-1/2 -translate-x-1/2 w-[800px] h-[400px] opacity-40 blur-3xl"
        style={{
          background: 'radial-gradient(ellipse at center, rgba(13, 148, 136, 0.35), rgba(20, 184, 166, 0.1), transparent 70%)',
        }}
      />
      <div
        className="pointer-events-none absolute -bottom-40 right-10 w-[500px] h-[350px] opacity-25 blur-3xl"
        style={{
          background: 'radial-gradient(ellipse at center, rgba(14, 165, 233, 0.3), transparent 70%)',
        }}
      />

      {/* Header bar accessible to all visitors */}
      <header className="relative z-10 w-full max-w-6xl mx-auto px-6 py-5 flex items-center justify-between">
        <Link to="/login" className="flex items-center gap-2.5 group">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-teal-600 to-teal-400 flex items-center justify-center shadow-sm shadow-teal-500/20 group-hover:scale-105 transition-transform">
            <Brain className="w-5 h-5 text-white" />
          </div>
          <div>
            <span className="text-lg font-bold tracking-tight text-slate-900">MemoryVault</span>
            <span className="hidden sm:inline-block ml-2 text-xs font-medium px-2 py-0.5 rounded-full bg-teal-50 text-teal-700 border border-teal-200/60">
              AI Powered
            </span>
          </div>
        </Link>

        {/* Prominent Extension Link (No login required!) */}
        <div className="flex items-center gap-3">
          <Link
            to="/extension"
            className="flex items-center gap-2 px-3.5 py-1.5 text-xs sm:text-sm font-medium text-teal-800 bg-teal-50 hover:bg-teal-100/80 border border-teal-200/80 rounded-lg shadow-xs transition-all hover:shadow-sm"
          >
            <Download className="w-4 h-4 text-teal-600" />
            <span>Get Chrome Extension</span>
            <span className="hidden md:inline text-[10px] uppercase font-bold tracking-wider px-1.5 py-0.5 bg-teal-200/60 text-teal-800 rounded">
              Free
            </span>
          </Link>
        </div>
      </header>

      {/* Main card container */}
      <main className="relative z-10 flex-1 flex items-center justify-center px-4 py-8 sm:py-12">
        <div className="w-full max-w-md">
          <div className="text-center mb-6">
            <h1 className="text-2xl font-bold tracking-tight text-slate-900">{title}</h1>
            {subtitle && <p className="text-sm text-slate-500 mt-1.5">{subtitle}</p>}
          </div>

          <div className="bg-white/90 backdrop-blur-md border border-slate-200/80 shadow-xl shadow-slate-200/50 rounded-2xl p-6 sm:p-8">
            {children}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="relative z-10 w-full max-w-6xl mx-auto px-6 py-5 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-500 border-t border-slate-200/60">
        <div className="flex items-center gap-2">
          <Sparkles className="w-3.5 h-3.5 text-teal-600" />
          <span>Intelligent link capture & automated resurfacing</span>
        </div>
        <div className="flex items-center gap-4">
          <Link to="/extension" className="hover:text-teal-700 font-medium transition-colors">
            Chrome Extension Guide
          </Link>
          <a
            href="/MemoryVault-Extension.zip"
            download="MemoryVault-Extension.zip"
            className="hover:text-teal-700 font-medium transition-colors"
          >
            Direct .ZIP Download
          </a>
          <span>© {new Date().getFullYear()} MemoryVault</span>
        </div>
      </footer>
    </div>
  )
}
