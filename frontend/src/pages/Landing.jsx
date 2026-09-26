import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import {
  Brain,
  Search,
  Sparkles,
  Zap,
  Bookmark,
  ExternalLink,
  ArrowRight,
  Download,
  CheckCircle2,
  Cpu,
  RefreshCw,
  Database,
  Globe,
  Smartphone,
  Mail,
  PlaySquare,
  Layers,
  BarChart3,
  Clock,
  Terminal,
  FileText,
  Share2,
  ChevronRight,
  Code2,
  Flame,
  Lightbulb,
  GraduationCap,
  Puzzle,
  Send,
} from 'lucide-react'
import { useAuthStore } from '../stores/authStore'

// Sample interactive vault items for the live playground
const SAMPLE_VAULT_ITEMS = [
  {
    id: 1,
    title: 'Designing Resilient Distributed Systems: Connection Pooling & Non-Blocking I/O',
    url: 'https://martinfowler.com/articles/distributed-systems.html',
    contentType: 'ARTICLE',
    badgeColor: 'bg-blue-50 text-blue-700 border-blue-200',
    category: 'Architecture',
    summary:
      'Deep dive into asynchronous pipelining, reactive connection pooling, and circuit breaker patterns to prevent cascading failures under 10k+ req/sec loads.',
    tags: ['Distributed Systems', 'Spring Boot', 'Resilience', 'Database'],
    resurfaceReason: '⚡ Resurfaced because you are searching for connection timeout handling',
    emotionalContext: 'Deep Study',
    importance: 9.8,
    dateSaved: '3 weeks ago',
  },
  {
    id: 2,
    title: 'Kafka Consumer Group Rebalances: Avoiding the Stop-The-World Trap',
    url: 'https://confluent.io/blog/kafka-rebalance-protocol-deep-dive',
    contentType: 'THREAD',
    badgeColor: 'bg-sky-50 text-sky-700 border-sky-200',
    category: 'Streaming',
    summary:
      'Step-by-step breakdown of incremental cooperative rebalancing in Apache Kafka vs eager rebalancing, eliminating consumer pauses during node rollouts.',
    tags: ['Kafka', 'Microservices', 'Event Streaming', 'Concurrency'],
    resurfaceReason: '🧠 Matches your recent review of event-driven batch pipelines',
    emotionalContext: 'Critical Fix',
    importance: 9.4,
    dateSaved: '1 month ago',
  },
  {
    id: 3,
    title: 'Building Production RAG Pipelines with Vector Search & Cosine Embeddings',
    url: 'https://youtube.com/watch?v=rag-production-architecture',
    contentType: 'VIDEO',
    badgeColor: 'bg-red-50 text-red-700 border-red-200',
    category: 'AI / Search',
    summary:
      'Architecture lecture covering chunking strategies, dense vector embeddings (MiniLM/nomic), HNSW indexes, and real-time semantic retrieval.',
    tags: ['Vector Embeddings', 'AI Pipeline', 'Semantic Search', 'Ollama'],
    resurfaceReason: '🎥 Synced from your YouTube Watch Later playlist · Ready for review',
    emotionalContext: 'Inspired',
    importance: 9.1,
    dateSaved: '5 days ago',
  },
  {
    id: 4,
    title: 'PostgreSQL Index Tuning: When B-Trees Fail and GIN/BRIN Save the Day',
    url: 'https://postgresweekly.com/issues/index-performance',
    contentType: 'ARTICLE',
    badgeColor: 'bg-blue-50 text-blue-700 border-blue-200',
    category: 'Database',
    summary:
      'Practical benchmarking comparing index bloat, partial indexes, and execution plans (EXPLAIN ANALYZE) across tables with 50M+ rows.',
    tags: ['PostgreSQL', 'SQL Optimization', 'Indexing', 'Performance'],
    resurfaceReason: '⏱️ Scheduled for spaced repetition review based on time-decay algorithm',
    emotionalContext: 'Deep Study',
    importance: 8.9,
    dateSaved: '2 months ago',
  },
  {
    id: 5,
    title: 'System Design Blueprint: Rate Limiting Algorithms in Distributed Environments',
    url: 'https://github.com/donnemartin/system-design-primer',
    contentType: 'REPO',
    badgeColor: 'bg-slate-100 text-slate-800 border-slate-300',
    category: 'Architecture',
    summary:
      'Comparison of Token Bucket, Leaky Bucket, Fixed Window, and Sliding Window Log algorithms with distributed Redis token synchronization.',
    tags: ['System Design', 'Redis', 'Rate Limiting', 'APIs'],
    resurfaceReason: '💡 In-context overlay hit while reviewing API gateway design',
    emotionalContext: 'Reference',
    importance: 9.6,
    dateSaved: '6 weeks ago',
  },
  {
    id: 6,
    title: 'Migrating from DynamoDB back to Postgres: How We Reduced Costs by 78%',
    url: 'https://blog.discord.com/how-discord-stores-billions-of-messages',
    contentType: 'ARTICLE',
    badgeColor: 'bg-blue-50 text-blue-700 border-blue-200',
    category: 'Case Study',
    summary:
      'Engineering retrospective on data modeling, partition keys, read/write provisioning bottlenecks, and partitioning strategies in relational databases.',
    tags: ['PostgreSQL', 'Cloud Cost', 'Data Architecture', 'Fintech'],
    resurfaceReason: '🔍 Semantic match for query: "database migration cost savings"',
    emotionalContext: 'Curious',
    importance: 8.7,
    dateSaved: '2 weeks ago',
  },
]

// Detailed real-world use cases
const USE_CASES = [
  {
    id: 'engineer',
    badge: 'Software Engineers & Architects',
    icon: Code2,
    color: 'teal',
    title: 'Never re-google a problem you already solved',
    headline: 'Turns fragmented bookmarks into a contextual knowledge co-pilot.',
    description:
      'How many times have you spent 45 minutes debugging a database deadlock or Kafka configuration error, only to realize you solved the exact same thing 4 months ago? MemoryVault embeds and organizes every technical guide, GitHub repo, and architectural teardown you save.',
    keyPoints: [
      'Search DuckDuckGo or Google and your previously saved articles appear directly above results.',
      'Auto-extracts code snippets, architecture takeaways, and tags by language & framework.',
      'Vector semantic search finds articles even when you don’t remember the exact technical keyword.',
    ],
    realWorldExample: {
      action: 'You search Google for:',
      query: '"Spring Boot HikariCP connection timeout in production"',
      resultTitle: 'MemoryVault Contextual Overlay pops up:',
      resultBody: '"You saved an engineering blog on this 3 months ago: \'Tuning HikariCP Pool Size for High Concurrency MySQL\'. Highlighted fix: adjust maximumPoolSize and leakDetectionThreshold."',
    },
  },
  {
    id: 'learning',
    badge: 'Lifelong Learners & Students',
    icon: GraduationCap,
    color: 'indigo',
    title: 'Escape the "Bookmark Graveyard" with Spaced Rediscovery',
    headline: 'Transform passive saving into active recall and genuine retention.',
    description:
      'Saving a link feels like learning, but bookmarks simply gather digital dust. MemoryVault’s ResurfaceEngine utilizes algorithmic spaced repetition. It analyzes content importance, life context, and elapsed days to resurface reading material right when your mind is ready.',
    keyPoints: [
      'Proactive daily rediscovery feed suggests 3–5 high-value items based on decay scoring.',
      'Automated weekly knowledge digest delivered directly via notification or email.',
      'Categorizes by emotional context (Deep Study, Inspiration, Quick Reference) so you read with intention.',
    ],
    realWorldExample: {
      action: 'Sunday Morning Digest:',
      query: 'Smart Queue Notification',
      resultTitle: '3 items worth revisiting today:',
      resultBody: '"1. System Design: Distributed Consensus (Saved 21 days ago) · 2. Tech Talk: Building Vector Databases (Saved 14 days ago) · 3. Thread: 10 SQL Profiling Tricks (Saved 45 days ago)"',
    },
  },
  {
    id: 'research',
    badge: 'Tech Leads & Content Curators',
    icon: Lightbulb,
    color: 'amber',
    title: 'Unified Capture Across YouTube, Twitter, and Mobile',
    headline: 'One intelligent inbox for all reading channels.',
    description:
      'Your reading list is scattered across YouTube Watch Later, Twitter bookmarks, Reddit saved posts, and mobile browser tabs. MemoryVault provides 6 seamless ingestion channels that all funnel into a single vector-indexed vault.',
    keyPoints: [
      'Chrome Extension with floating 1-click capture button on every webpage.',
      'Automated 6-hourly background sync with your YouTube Watch Later playlist.',
      'Android PWA native share-target: share directly from Twitter/Reddit apps into your vault.',
      'Forward links to your dedicated vault email address with zero browser needed.',
    ],
    realWorldExample: {
      action: 'Mobile Workflow:',
      query: 'Browsing Twitter on phone → Tap "Share via MemoryVault"',
      resultTitle: 'Immediate Backend Enrichment:',
      resultBody: 'Thread is captured, text scraped, summarized by LLM, embedded in 384 dimensions, and tagged #SystemDesign #DistributedSystems within 2 seconds.',
    },
  },
]

// Featured plugins for ecosystem showcase
const FEATURED_PLUGINS = [
  {
    id: 'obsidian',
    name: 'Obsidian Local Vault',
    category: 'Workflow & Export',
    icon: FileText,
    iconBg: 'bg-purple-600 text-white',
    badge: 'Bi-directional Sync',
    description: 'Auto-exports memory cards into local markdown files with YAML frontmatter, tags, and internal wikilinks.',
    stats: '142 notes synced',
    status: 'Active',
    tags: ['Markdown', 'YAML', 'Wikilinks', 'Local First'],
  },
  {
    id: 'github',
    name: 'GitHub Stars & Repos',
    category: 'Ingestion',
    icon: Code2,
    iconBg: 'bg-slate-900 text-white',
    badge: 'Auto-Tagging',
    description: 'Syncs starred repositories and bookmarked issues. Extracts README architecture and tags by primary language.',
    stats: '28 repos indexed',
    status: 'Active',
    tags: ['Repositories', 'README Parser', 'Language Detection'],
  },
  {
    id: 'notion',
    name: 'Notion Database Exporter',
    category: 'Workflow & Export',
    icon: Database,
    iconBg: 'bg-neutral-800 text-white',
    badge: 'Relational DB',
    description: 'Mirrors vault cards into customizable Notion tables, Kanban boards, and multi-select property fields.',
    stats: 'Ready to connect',
    status: 'Ready',
    tags: ['Table View', 'Multi-Select', 'Property Mapping'],
  },
  {
    id: 'raycast',
    name: 'Raycast & Spotlight',
    category: 'Desktop & Surface',
    icon: Terminal,
    iconBg: 'bg-rose-600 text-white',
    badge: 'Global Shortcut',
    description: 'Search your vault directly from macOS Spotlight or Raycast using ⌥ Space. Save clipboard links with 1 keystroke.',
    stats: 'Native Extension',
    status: 'Installed',
    tags: ['macOS', 'Keyboard First', 'Clipboard Monitor'],
  },
  {
    id: 'youtube',
    name: 'YouTube Watch Later',
    category: 'Ingestion',
    icon: PlaySquare,
    iconBg: 'bg-red-600 text-white',
    badge: 'Auto-Transcribe',
    description: 'Syncs your Watch Later playlist every 6 hours. Automatically transcribes video audio into searchable cards.',
    stats: '19 videos synced',
    status: 'Active',
    tags: ['Video AI', 'Audio Transcripts', 'Timestamps'],
  },
  {
    id: 'telegram',
    name: 'Telegram & Slack Bots',
    category: 'Ingestion',
    icon: Send,
    iconBg: 'bg-sky-600 text-white',
    badge: 'Direct Message',
    description: 'DM links, voice memos, or code snippets directly to @MemoryVaultBot. Instant async web scraping & embedding.',
    stats: 'Real-time webhook',
    status: 'Active',
    tags: ['Chatbot', 'Voice Notes', 'Push Notifications'],
  },
  {
    id: 'webhook',
    name: 'Developer Webhooks & REST',
    category: 'Developer SDK',
    icon: Zap,
    iconBg: 'bg-amber-600 text-white',
    badge: 'Inbound API',
    description: 'Ingest links from custom bash scripts, iOS Shortcuts, IFTTT, or GitHub Actions with personal API tokens.',
    stats: 'cURL & REST SDK',
    status: 'Ready',
    tags: ['REST API', 'JSON Webhook', 'cURL', 'CI/CD'],
  },
  {
    id: 'anki',
    name: 'Anki Flashcard Generator',
    category: 'Workflow & Export',
    icon: GraduationCap,
    iconBg: 'bg-emerald-600 text-white',
    badge: 'Spaced Repetition',
    description: 'Converts key takeaways and architecture summaries into .apkg flashcard decks for spaced repetition review.',
    stats: 'SM-2 Algorithm',
    status: 'Ready',
    tags: ['Flashcards', 'APKG Export', 'Active Recall'],
  },
]

export default function Landing() {
  const user = useAuthStore((s) => s.user)
  const accessToken = useAuthStore((s) => s.accessToken)

  const [activeUseCase, setActiveUseCase] = useState(0)
  const [selectedCategory, setSelectedCategory] = useState('All')
  const [selectedPluginCategory, setSelectedPluginCategory] = useState('All')
  const [searchQuery, setSearchQuery] = useState('')

  // Filter sample playground items
  const filteredItems = useMemo(() => {
    return SAMPLE_VAULT_ITEMS.filter((item) => {
      const matchesCategory =
        selectedCategory === 'All' || item.category === selectedCategory || item.contentType === selectedCategory
      const query = searchQuery.toLowerCase().trim()
      const matchesQuery =
        !query ||
        item.title.toLowerCase().includes(query) ||
        item.summary.toLowerCase().includes(query) ||
        item.tags.some((t) => t.toLowerCase().includes(query)) ||
        item.category.toLowerCase().includes(query)
      return matchesCategory && matchesQuery
    })
  }, [selectedCategory, searchQuery])

  const categories = ['All', 'Architecture', 'Streaming', 'AI / Search', 'Database', 'Case Study']

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900 font-sans selection:bg-teal-500/20 selection:text-teal-900">
      {/* ── Background Glow Orbs ────────────────────────────────────────── */}
      <div className="fixed inset-0 pointer-events-none overflow-hidden -z-10">
        <div
          className="absolute -top-40 left-1/2 -translate-x-1/2 w-[1000px] h-[550px] opacity-40 blur-3xl"
          style={{
            background:
              'radial-gradient(ellipse at center, rgba(13, 148, 136, 0.28), rgba(20, 184, 166, 0.08), transparent 70%)',
          }}
        />
        <div
          className="absolute top-[35%] -right-40 w-[600px] h-[450px] opacity-25 blur-3xl"
          style={{
            background: 'radial-gradient(ellipse at center, rgba(14, 165, 233, 0.25), transparent 70%)',
          }}
        />
        <div
          className="absolute bottom-10 -left-32 w-[550px] h-[400px] opacity-20 blur-3xl"
          style={{
            background: 'radial-gradient(ellipse at center, rgba(99, 102, 241, 0.2), transparent 70%)',
          }}
        />
      </div>

      {/* ── Top Announcement Banner (if user already logged in) ────────── */}
      {accessToken && user && (
        <div className="bg-teal-900 text-teal-100 px-4 py-2.5 text-xs sm:text-sm text-center font-medium flex items-center justify-center gap-2">
          <Sparkles className="w-4 h-4 text-teal-400" />
          <span>Welcome back, {user.displayName || user.email}! You are currently logged in.</span>
          <Link
            to="/dashboard"
            className="ml-2 inline-flex items-center gap-1 font-semibold text-white underline hover:text-teal-200 transition-colors"
          >
            Go to your Dashboard <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
      )}

      {/* ── Navigation Header ───────────────────────────────────────────── */}
      <header className="sticky top-0 z-40 bg-white/85 backdrop-blur-md border-b border-slate-200/80 transition-all">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 group focus:outline-none">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-teal-600 to-teal-400 flex items-center justify-center shadow-sm shadow-teal-500/20 group-hover:scale-105 transition-transform">
              <Brain className="w-5 h-5 text-white" />
            </div>
            <div className="flex items-center gap-2">
              <span className="text-lg font-bold tracking-tight text-slate-900">MemoryVault</span>
              <span className="hidden sm:inline-block text-[11px] font-semibold px-2 py-0.5 rounded-full bg-teal-50 text-teal-700 border border-teal-200/60">
                Second Brain AI
              </span>
            </div>
          </Link>

          {/* Center Links */}
          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600">
            <a href="#use-cases" className="hover:text-teal-600 transition-colors">
              Use Cases
            </a>
            <a href="#how-it-works" className="hover:text-teal-600 transition-colors">
              How It Works
            </a>
            <a href="#interactive-vault" className="hover:text-teal-600 transition-colors">
              Live Demo
            </a>
            <a href="#plugins" className="hover:text-teal-600 transition-colors flex items-center gap-1">
              Plugins
              <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 bg-purple-100 text-purple-800 rounded">
                Hub
              </span>
            </a>
            <a href="#architecture" className="hover:text-teal-600 transition-colors">
              AI Stack
            </a>
            <Link to="/extension" className="hover:text-teal-600 transition-colors flex items-center gap-1">
              Extension
              <span className="text-[10px] font-bold uppercase tracking-wider px-1.5 py-0.5 bg-teal-100 text-teal-800 rounded">
                v3
              </span>
            </Link>
          </nav>

          {/* Right Action Buttons */}
          <div className="flex items-center gap-2.5 sm:gap-3">
            {accessToken ? (
              <Link
                to="/dashboard"
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg text-sm font-medium bg-teal-600 hover:bg-teal-700 text-white shadow-xs transition-colors"
              >
                Open Dashboard <ArrowRight className="w-4 h-4" />
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-3.5 py-1.5 text-sm font-medium text-slate-700 hover:text-slate-900 hover:bg-slate-100 rounded-lg transition-colors"
                >
                  Sign in
                </Link>
                <Link
                  to="/register"
                  className="inline-flex items-center gap-1 px-4 py-2 text-sm font-medium bg-teal-600 hover:bg-teal-700 text-white rounded-lg shadow-xs hover:shadow transition-all"
                >
                  Get Started Free
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* ── HERO SECTION ─────────────────────────────────────────────────── */}
      <section className="relative pt-12 pb-16 sm:pt-20 sm:pb-24 px-4 sm:px-6">
        <div className="max-w-5xl mx-auto text-center">
          {/* Pill Badge */}
          <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-teal-50 border border-teal-200/80 text-teal-800 text-xs sm:text-sm font-medium mb-6 shadow-xs animate-fade-in">
            <Sparkles className="w-4 h-4 text-teal-600 animate-pulse" />
            <span>Never let another saved article rot in a bookmarks folder</span>
          </div>

          {/* Hero Headline */}
          <h1 className="text-3xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight text-slate-900 leading-[1.15]">
            The <span className="text-transparent bg-clip-text bg-gradient-to-r from-teal-600 via-teal-500 to-sky-600">intelligence layer</span>{' '}
            over your saved knowledge.
          </h1>

          {/* Subtitle */}
          <p className="mt-6 text-base sm:text-lg lg:text-xl text-slate-600 max-w-3xl mx-auto leading-relaxed">
            People save hundreds of technical blogs, system design posts, YouTube talks, and threads they swear they’ll
            revisit — and almost never do. <strong>MemoryVault</strong> vector-embeds what you save, understands the
            context, and <strong>proactively resurfaces forgotten insights right when you actually need them.</strong>
          </p>

          {/* CTAs */}
          <div className="mt-8 sm:mt-10 flex flex-wrap items-center justify-center gap-3 sm:gap-4">
            <a
              href="#interactive-vault"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl text-sm sm:text-base font-semibold text-white bg-teal-600 hover:bg-teal-700 shadow-md shadow-teal-600/20 hover:shadow-lg transition-all"
            >
              Explore Live Demo <ArrowRight className="w-4 h-4" />
            </a>
            <Link
              to="/extension"
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl text-sm sm:text-base font-semibold text-slate-700 bg-white hover:bg-slate-50 border border-slate-300 shadow-xs hover:border-slate-400 transition-all"
            >
              <Download className="w-4 h-4 text-teal-600" />
              Get Chrome Extension
            </Link>
            {!accessToken && (
              <Link
                to="/register"
                className="inline-flex items-center gap-2 px-5 py-3 rounded-xl text-sm sm:text-base font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all"
              >
                Create Account <ChevronRight className="w-4 h-4" />
              </Link>
            )}
          </div>

          {/* Trust points */}
          <div className="mt-8 flex flex-wrap items-center justify-center gap-6 text-xs sm:text-sm text-slate-500">
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-teal-600" />
              Zero paid API keys required
            </span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-teal-600" />
              384-dimensional vector semantic search
            </span>
            <span className="flex items-center gap-1.5">
              <CheckCircle2 className="w-4 h-4 text-teal-600" />
              Chrome Manifest V3 compliant
            </span>
          </div>

          {/* ── Search Overlay Mockup / GIF Preview ───────────────────────── */}
          <div className="mt-12 sm:mt-16 max-w-4xl mx-auto rounded-2xl border border-slate-200/90 bg-white shadow-2xl shadow-slate-200/60 overflow-hidden">
            {/* Window bar */}
            <div className="flex items-center justify-between px-4 py-3 bg-slate-100 border-b border-slate-200">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-rose-400" />
                <span className="w-3 h-3 rounded-full bg-amber-400" />
                <span className="w-3 h-3 rounded-full bg-emerald-400" />
                <span className="ml-2 font-mono text-xs text-slate-500">
                  DuckDuckGo + MemoryVault Chrome Extension Overlay (Live Capture)
                </span>
              </div>
              <span className="text-[11px] font-semibold text-teal-800 bg-teal-100/80 px-2 py-0.5 rounded">
                Actual Extension In Action
              </span>
            </div>
            {/* Visual demo content */}
            <div className="p-4 sm:p-6 bg-slate-900 text-left">
              <div className="relative rounded-xl overflow-hidden border border-slate-800 shadow-inner bg-slate-950 flex flex-col items-center justify-center">
                <img
                  src="/search-overlay-demo.gif"
                  alt="MemoryVault Search Overlay appearing above search results"
                  className="w-full max-h-[460px] object-cover object-top"
                  loading="lazy"
                />
              </div>
              <div className="mt-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs text-slate-400">
                <div className="flex items-center gap-2 text-slate-300">
                  <Search className="w-4 h-4 text-teal-400" />
                  <span>
                    When typing query in Google / DuckDuckGo, your own saved knowledge surfaces above search results.
                  </span>
                </div>
                <Link
                  to="/extension"
                  className="inline-flex items-center gap-1 text-teal-400 hover:text-teal-300 font-semibold underline shrink-0"
                >
                  See Extension Setup Guide <ArrowRight className="w-3 h-3" />
                </Link>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* ── SECTION: REAL-WORLD USE CASES (Requested Focus) ──────────────── */}
      <section id="use-cases" className="py-16 sm:py-24 bg-white border-y border-slate-200/80">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-3xl mx-auto mb-12 sm:mb-16">
            <span className="text-xs font-bold uppercase tracking-wider text-teal-600 bg-teal-50 px-3 py-1 rounded-full border border-teal-200/60">
              Why MemoryVault Exists
            </span>
            <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
              Built for real engineering & research workflows.
            </h2>
            <p className="mt-4 text-base sm:text-lg text-slate-600">
              Traditional bookmarks are where knowledge goes to be forgotten. Here is how engineers, researchers, and
              curators use MemoryVault every day.
            </p>
          </div>

          {/* Use Case Tabs */}
          <div className="flex justify-center gap-2 mb-10 overflow-x-auto pb-2">
            {USE_CASES.map((uc, idx) => {
              const Icon = uc.icon
              const isActive = activeUseCase === idx
              return (
                <button
                  key={uc.id}
                  onClick={() => setActiveUseCase(idx)}
                  className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all whitespace-nowrap cursor-pointer ${
                    isActive
                      ? 'bg-slate-900 text-white shadow-md'
                      : 'bg-slate-100 text-slate-600 hover:bg-slate-200/80 hover:text-slate-900'
                  }`}
                >
                  <Icon className={`w-4 h-4 ${isActive ? 'text-teal-400' : 'text-slate-500'}`} />
                  <span>{uc.badge}</span>
                </button>
              )
            })}
          </div>

          {/* Active Tab Card */}
          {(() => {
            const current = USE_CASES[activeUseCase]
            const Icon = current.icon
            return (
              <div className="grid lg:grid-cols-12 gap-8 items-center bg-slate-50 border border-slate-200 rounded-3xl p-6 sm:p-10 shadow-sm">
                {/* Left Description Column */}
                <div className="lg:col-span-7 space-y-6">
                  <div className="inline-flex items-center gap-2 px-3 py-1 rounded-lg text-xs font-semibold bg-teal-100/70 text-teal-800">
                    <Icon className="w-3.5 h-3.5 text-teal-700" />
                    <span>{current.badge}</span>
                  </div>

                  <h3 className="text-2xl sm:text-3xl font-bold text-slate-900 leading-snug">{current.title}</h3>
                  <p className="text-base text-slate-600 leading-relaxed">{current.description}</p>

                  <div className="space-y-3 pt-2">
                    {current.keyPoints.map((pt, i) => (
                      <div key={i} className="flex items-start gap-3">
                        <CheckCircle2 className="w-5 h-5 text-teal-600 shrink-0 mt-0.5" />
                        <span className="text-sm font-medium text-slate-700 leading-relaxed">{pt}</span>
                      </div>
                    ))}
                  </div>
                </div>

                {/* Right Interactive Scenario Box */}
                <div className="lg:col-span-5 bg-white border border-slate-200/90 rounded-2xl p-5 sm:p-6 shadow-md shadow-slate-200/50 space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-slate-100">
                    <span className="text-xs font-bold text-slate-400 uppercase tracking-wider">
                      Real-Life Scenario
                    </span>
                    <span className="text-xs font-mono text-teal-700 bg-teal-50 px-2 py-0.5 rounded">
                      Live Simulation
                    </span>
                  </div>

                  <div>
                    <span className="text-xs text-slate-400">{current.realWorldExample.action}</span>
                    <div className="mt-1 font-mono text-xs sm:text-sm bg-slate-900 text-teal-300 p-3 rounded-lg flex items-center gap-2">
                      <Terminal className="w-4 h-4 text-slate-500 shrink-0" />
                      <span>{current.realWorldExample.query}</span>
                    </div>
                  </div>

                  <div className="bg-teal-50/70 border border-teal-200/80 rounded-xl p-4 space-y-2">
                    <div className="flex items-center gap-2 text-xs font-bold text-teal-900">
                      <Sparkles className="w-4 h-4 text-teal-600 shrink-0" />
                      <span>{current.realWorldExample.resultTitle}</span>
                    </div>
                    <p className="text-xs sm:text-sm text-teal-950 font-normal leading-relaxed">
                      {current.realWorldExample.resultBody}
                    </p>
                  </div>

                  <div className="pt-2 text-center">
                    <Link
                      to="/register"
                      className="inline-flex items-center gap-1.5 text-xs font-semibold text-teal-700 hover:text-teal-900 transition-colors"
                    >
                      Try this workflow on your links →
                    </Link>
                  </div>
                </div>
              </div>
            )
          })()}

          {/* 3 Quick Use Case Pillars */}
          <div className="mt-12 grid md:grid-cols-3 gap-6">
            <div className="bg-slate-50/80 border border-slate-200 rounded-2xl p-6 hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-blue-100 text-blue-700 flex items-center justify-center mb-4">
                <Bookmark className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-slate-900 text-lg mb-2">1. The "Read Later" Pipeline</h4>
              <p className="text-sm text-slate-600 leading-relaxed">
                Save with 1-click while reading. MemoryVault scrapes title, OpenGraph tags, key takeaways, and schedules
                the article for weekend reading sessions.
              </p>
            </div>

            <div className="bg-slate-50/80 border border-slate-200 rounded-2xl p-6 hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-700 flex items-center justify-center mb-4">
                <Brain className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-slate-900 text-lg mb-2">2. Vector Semantic Retrieval</h4>
              <p className="text-sm text-slate-600 leading-relaxed">
                Search by concept rather than exact words. Type "distributed locking with redis" or "pricing strategies"
                and 384-d vector embeddings find the exact match.
              </p>
            </div>

            <div className="bg-slate-50/80 border border-slate-200 rounded-2xl p-6 hover:shadow-md transition-shadow">
              <div className="w-10 h-10 rounded-xl bg-amber-100 text-amber-700 flex items-center justify-center mb-4">
                <RefreshCw className="w-5 h-5" />
              </div>
              <h4 className="font-bold text-slate-900 text-lg mb-2">3. Decay-Based Spaced Recall</h4>
              <p className="text-sm text-slate-600 leading-relaxed">
                A mathematical decay algorithm balances recency, interaction frequency, and importance to surface 3–5
                items every morning so your memory stays razor sharp.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* ── SECTION: 6 INGESTION CHANNELS (How Data Enters Vault) ────────── */}
      <section id="how-it-works" className="py-16 sm:py-24 max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-3xl mx-auto mb-14">
          <span className="text-xs font-bold uppercase tracking-wider text-teal-600 bg-teal-50 px-3 py-1 rounded-full border border-teal-200/60">
            Omnichannel Capture
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            6 Ways Knowledge Lands in Your Vault
          </h2>
          <p className="mt-4 text-base sm:text-lg text-slate-600">
            You don't change how you browse. MemoryVault plugs into where you already find information.
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {/* Channel 1 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-teal-50 text-teal-700 flex items-center justify-center mb-4 border border-teal-200/60">
              <Zap className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">1. Chrome Extension Floating Save</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              A discrete, floating '+' button appears on articles, Medium posts, Substack, and Reddit. 1 click captures
              and enriches the page in the background.
            </p>
          </div>

          {/* Channel 2 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-sky-50 text-sky-700 flex items-center justify-center mb-4 border border-sky-200/60">
              <Search className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">2. Contextual Search Overlay</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              When searching DuckDuckGo or Google, the extension silently queries your vault embeddings and displays
              relevant saved cards directly over the search engine.
            </p>
          </div>

          {/* Channel 3 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-red-50 text-red-700 flex items-center justify-center mb-4 border border-red-200/60">
              <PlaySquare className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">3. YouTube Playlist Auto-Sync</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              Connect YouTube OAuth once. A 6-hourly background task automatically pulls videos from your 'Watch Later'
              playlist and extracts transcripts and key takeaways.
            </p>
          </div>

          {/* Channel 4 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-700 flex items-center justify-center mb-4 border border-purple-200/60">
              <Smartphone className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">4. Android PWA Native Share Target</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              Install the Progressive Web App on mobile. MemoryVault appears as a native destination inside Android's
              system share sheet from Twitter, LinkedIn, or Chrome.
            </p>
          </div>

          {/* Channel 5 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-700 flex items-center justify-center mb-4 border border-amber-200/60">
              <Mail className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">5. Email Ingestion (SMTP / Mailgun)</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              Forward newsletters, links, or client emails to your personalized vault email address. The async parser
              extracts the URLs and enqueues them for enrichment.
            </p>
          </div>

          {/* Channel 6 */}
          <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-xs hover:shadow-md transition-all">
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-700 flex items-center justify-center mb-4 border border-emerald-200/60">
              <Layers className="w-5 h-5" />
            </div>
            <h3 className="font-bold text-slate-900 text-base mb-1.5">6. Web App Quick-Save Modal</h3>
            <p className="text-sm text-slate-600 leading-relaxed">
              Paste any URL directly inside the web dashboard. Add optional emotional context, custom tags, or notes to
              override AI defaults before saving.
            </p>
          </div>
        </div>
      </section>

      {/* ── SECTION: INTERACTIVE VAULT EXPLORER (Live Demo for Visitors) ─── */}
      <section id="interactive-vault" className="py-16 sm:py-24 bg-slate-100/70 border-y border-slate-200">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-10">
            <div>
              <span className="text-xs font-bold uppercase tracking-wider text-teal-600 bg-teal-50 px-3 py-1 rounded-full border border-teal-200/60">
                Interactive Showcase
              </span>
              <h2 className="mt-3 text-3xl font-extrabold text-slate-900 tracking-tight">
                Try the Vault Explorer Live
              </h2>
              <p className="mt-2 text-sm sm:text-base text-slate-600 max-w-xl">
                Experience how MemoryVault organizes, tags, and annotates knowledge cards. Filter by topic or search
                conceptually below:
              </p>
            </div>

            {/* Live Search Bar */}
            <div className="relative w-full md:w-80">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by topic, keyword, or mood..."
                className="w-full bg-white border border-slate-300 rounded-xl pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500 focus:border-teal-500 shadow-xs"
              />
              {searchQuery && (
                <button
                  onClick={() => setSearchQuery('')}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 hover:text-slate-600"
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {/* Filter Pills */}
          <div className="flex flex-wrap gap-2 mb-8">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedCategory(cat)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                  selectedCategory === cat
                    ? 'bg-teal-600 text-white shadow-xs'
                    : 'bg-white text-slate-600 hover:bg-slate-200/80 border border-slate-200'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          {/* Items Grid */}
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filteredItems.map((item) => (
              <div
                key={item.id}
                className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs hover:shadow-md hover:border-teal-300 transition-all flex flex-col justify-between"
              >
                <div>
                  {/* Top Badges */}
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <span className={`text-[11px] font-bold px-2 py-0.5 rounded-md border ${item.badgeColor}`}>
                      {item.contentType}
                    </span>
                    <span className="text-[11px] font-mono text-slate-400">{item.dateSaved}</span>
                  </div>

                  {/* Title */}
                  <a
                    href={item.url}
                    target="_blank"
                    rel="noreferrer"
                    className="group block font-semibold text-slate-900 text-base leading-snug hover:text-teal-600 transition-colors mb-2.5"
                  >
                    <span className="line-clamp-2">{item.title}</span>
                    <ExternalLink className="inline-block w-3.5 h-3.5 text-slate-400 ml-1.5 opacity-0 group-hover:opacity-100 transition-opacity" />
                  </a>

                  {/* AI Summary */}
                  <p className="text-xs text-slate-600 leading-relaxed line-clamp-3 mb-4">{item.summary}</p>
                </div>

                <div>
                  {/* Resurface Reason Notification Callout */}
                  <div className="bg-teal-50/80 border border-teal-200/60 rounded-xl p-2.5 mb-3 text-[11px] font-medium text-teal-900 leading-tight">
                    {item.resurfaceReason}
                  </div>

                  {/* Tags */}
                  <div className="flex flex-wrap gap-1.5 pt-2 border-t border-slate-100">
                    {item.tags.map((t) => (
                      <span
                        key={t}
                        className="text-[10px] font-mono bg-slate-100 text-slate-600 px-2 py-0.5 rounded"
                      >
                        #{t}
                      </span>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {filteredItems.length === 0 && (
            <div className="bg-white border border-slate-200 rounded-2xl p-12 text-center text-slate-500">
              <Search className="w-8 h-8 text-slate-300 mx-auto mb-3" />
              <p className="font-semibold text-slate-700">No sample cards match "{searchQuery}"</p>
              <p className="text-xs text-slate-400 mt-1">Try searching "kafka", "postgres", "distributed", or "ai"</p>
            </div>
          )}

          {/* Bottom Banner */}
          <div className="mt-10 bg-white border border-slate-200 rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-4 text-center sm:text-left">
            <div>
              <h4 className="font-bold text-slate-900 text-base">Ready to organize your real reading list?</h4>
              <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                Sign up in 30 seconds. No credit card or API keys required.
              </p>
            </div>
            <Link
              to="/register"
              className="px-5 py-2.5 rounded-xl text-sm font-semibold bg-teal-600 hover:bg-teal-700 text-white shadow-xs hover:shadow transition-all shrink-0"
            >
              Start Your Vault Free →
            </Link>
          </div>
        </div>
      </section>

      {/* ── SECTION: AI STACK & TECHNICAL ARCHITECTURE ───────────────────── */}
      <section id="architecture" className="py-16 sm:py-24 max-w-6xl mx-auto px-4 sm:px-6">
        <div className="text-center max-w-3xl mx-auto mb-14">
          <span className="text-xs font-bold uppercase tracking-wider text-teal-600 bg-teal-50 px-3 py-1 rounded-full border border-teal-200/60">
            Open-Source Engineering
          </span>
          <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
            Zero Paid API Keys. Pure Open-Source AI.
          </h2>
          <p className="mt-4 text-base sm:text-lg text-slate-600">
            MemoryVault doesn't tie you to expensive commercial LLM tokens. It runs completely free via local Ollama
            or free-tier hosted inference.
          </p>
        </div>

        {/* Architecture Table */}
        <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden shadow-xs mb-10">
          <div className="px-6 py-4 bg-slate-900 text-white flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Cpu className="w-5 h-5 text-teal-400" />
              <span className="font-semibold text-sm sm:text-base">AIService Architecture & Profiles</span>
            </div>
            <span className="text-xs font-mono text-emerald-400 bg-emerald-950/80 px-2 py-0.5 rounded border border-emerald-800">
              Cost: $0.00 / mo
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm border-collapse">
              <thead>
                <tr className="bg-slate-50 border-b border-slate-200 text-xs font-semibold text-slate-500 uppercase tracking-wider">
                  <th className="py-3 px-6">Spring Profile</th>
                  <th className="py-3 px-6">Text Generation / Summaries</th>
                  <th className="py-3 px-6">Vector Embeddings</th>
                  <th className="py-3 px-6">Infrastructure</th>
                  <th className="py-3 px-6">Cost</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-mono text-xs sm:text-sm">
                <tr className="hover:bg-slate-50/50">
                  <td className="py-4 px-6 font-semibold text-teal-700">dev (Ollama)</td>
                  <td className="py-4 px-6 text-slate-700">llama3.1:8b (Local Docker container)</td>
                  <td className="py-4 px-6 text-slate-700">nomic-embed-text</td>
                  <td className="py-4 px-6 text-slate-500">Local Docker compose (port 11434)</td>
                  <td className="py-4 px-6 text-emerald-600 font-bold">$0 (Offline)</td>
                </tr>
                <tr className="hover:bg-slate-50/50">
                  <td className="py-4 px-6 font-semibold text-sky-700">prod (Groq + HF)</td>
                  <td className="py-4 px-6 text-slate-700">llama-3.1-8b-instant (Groq Cloud API)</td>
                  <td className="py-4 px-6 text-slate-700">sentence-transformers/all-MiniLM-L6-v2</td>
                  <td className="py-4 px-6 text-slate-500">Render (PostgreSQL) + Vercel</td>
                  <td className="py-4 px-6 text-emerald-600 font-bold">Free Tier ($0)</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        {/* Tech Stack Pillars */}
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5">
          <div className="bg-slate-50 border border-slate-200 rounded-xl p-5">
            <div className="flex items-center gap-2 text-teal-700 font-bold text-sm mb-2">
              <Database className="w-4 h-4" />
              <span>Spring Boot 3.2</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Java 17 backend with @Async enrichment pipeline, JDBC pooling, and automated schema migration.
            </p>
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-xl p-5">
            <div className="flex items-center gap-2 text-sky-700 font-bold text-sm mb-2">
              <Layers className="w-4 h-4" />
              <span>Vector Cosine Search</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Every item's title, summary, and tags are converted to a 384-dimensional vector float array for instant
              similarity matching.
            </p>
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-xl p-5">
            <div className="flex items-center gap-2 text-purple-700 font-bold text-sm mb-2">
              <Globe className="w-4 h-4" />
              <span>React 19 & Vite</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Blazing fast single-page app with Tailwind CSS 4, Zustand state management, and responsive chart analytics.
            </p>
          </div>

          <div className="bg-slate-50 border border-slate-200 rounded-xl p-5">
            <div className="flex items-center gap-2 text-amber-700 font-bold text-sm mb-2">
              <RefreshCw className="w-4 h-4" />
              <span>ResurfaceEngine</span>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Mathematical scoring balancing item importance, emotional tags, and temporal decay to schedule spaced
              rediscovery.
            </p>
          </div>
        </div>
      </section>

      {/* ── SECTION: EXTENSIBLE PLUGINS & CONNECTORS ───────────────────── */}
      <section id="plugins" className="py-16 sm:py-24 bg-slate-100/60 border-y border-slate-200">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-3xl mx-auto mb-12">
            <span className="text-xs font-bold uppercase tracking-wider text-purple-700 bg-purple-50 px-3 py-1 rounded-full border border-purple-200/60">
              Ecosystem & Integrations
            </span>
            <h2 className="mt-4 text-3xl sm:text-4xl font-extrabold text-slate-900 tracking-tight">
              Connect Your Entire Stack With Plugins
            </h2>
            <p className="mt-4 text-base sm:text-lg text-slate-600">
              MemoryVault isn't a closed silo. Connect your Obsidian vault, GitHub stars, Notion databases, Raycast
              launcher, Telegram bots, and custom webhooks—with zero vendor lock-in.
            </p>
          </div>

          {/* Plugin Category Filter */}
          <div className="flex flex-wrap justify-center gap-2 mb-10">
            {['All', 'Ingestion', 'Workflow & Export', 'Desktop & Surface', 'Developer SDK'].map((cat) => (
              <button
                key={cat}
                onClick={() => setSelectedPluginCategory(cat)}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all cursor-pointer ${
                  selectedPluginCategory === cat
                    ? 'bg-slate-900 text-white shadow-xs'
                    : 'bg-white text-slate-600 hover:bg-slate-200/80 border border-slate-200'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          {/* Plugins Grid */}
          <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-5 mb-12">
            {FEATURED_PLUGINS.filter(
              (p) => selectedPluginCategory === 'All' || p.category === selectedPluginCategory
            ).map((plugin) => {
              const Icon = plugin.icon
              return (
                <div
                  key={plugin.id}
                  className="bg-white border border-slate-200 rounded-2xl p-5 shadow-xs hover:shadow-md hover:border-purple-300 transition-all flex flex-col justify-between"
                >
                  <div>
                    <div className="flex items-start justify-between gap-3 mb-3">
                      <div className={`w-10 h-10 rounded-xl flex items-center justify-center shadow-xs ${plugin.iconBg}`}>
                        <Icon className="w-5 h-5" />
                      </div>
                      <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200">
                        {plugin.badge}
                      </span>
                    </div>

                    <h3 className="font-bold text-slate-900 text-base mb-1.5 leading-snug">{plugin.name}</h3>
                    <p className="text-xs text-slate-600 leading-relaxed mb-4 line-clamp-3">{plugin.description}</p>
                  </div>

                  <div>
                    {/* Status Badge */}
                    <div className="flex items-center justify-between text-[11px] font-mono text-slate-500 pt-3 border-t border-slate-100">
                      <span className="flex items-center gap-1.5 text-emerald-600 font-medium">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                        {plugin.status}
                      </span>
                      <span className="text-slate-400">{plugin.stats}</span>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Dual Interactive Previews (Obsidian Markdown vs Webhook) */}
          <div className="grid lg:grid-cols-2 gap-6 items-stretch mb-12">
            {/* Box 1: Obsidian Local Vault Export Sample */}
            <div className="bg-slate-900 text-slate-100 rounded-2xl p-6 border border-slate-800 shadow-lg flex flex-col justify-between font-mono text-xs">
              <div>
                <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-800">
                  <div className="flex items-center gap-2">
                    <FileText className="w-4 h-4 text-purple-400" />
                    <span className="font-semibold text-slate-200">Obsidian Note Output (.md)</span>
                  </div>
                  <span className="text-[11px] text-emerald-400 bg-emerald-950/80 px-2 py-0.5 rounded border border-emerald-800">
                    Auto-generated YAML
                  </span>
                </div>
                <pre className="text-slate-300 overflow-x-auto leading-relaxed whitespace-pre-wrap">
{`---
id: mv-9482
title: "Designing Resilient Distributed Systems"
url: "https://martinfowler.com/articles/distributed-systems.html"
category: Architecture
tags: [distributed-systems, spring-boot, resilience]
synced_at: 2026-09-27T00:50:00Z
resurface_score: 9.8
---

# Designing Resilient Distributed Systems
> Resurfaced by [[MemoryVault]] vector cosine similarity

## AI Executive Summary
Deep dive into asynchronous pipelining, reactive connection pooling,
and circuit breaker patterns to prevent cascading failures...`}
                </pre>
              </div>
              <div className="mt-4 pt-3 border-t border-slate-800/80 text-[11px] text-slate-400 flex items-center justify-between">
                <span>Direct disk sync into ~/Documents/Obsidian</span>
                <span className="text-purple-400 font-semibold">100% Offline Markdown</span>
              </div>
            </div>

            {/* Box 2: Developer Inbound Webhook / REST SDK */}
            <div className="bg-slate-900 text-slate-100 rounded-2xl p-6 border border-slate-800 shadow-lg flex flex-col justify-between font-mono text-xs">
              <div>
                <div className="flex items-center justify-between pb-3 mb-4 border-b border-slate-800">
                  <div className="flex items-center gap-2">
                    <Terminal className="w-4 h-4 text-amber-400" />
                    <span className="font-semibold text-slate-200">Inbound Webhook Trigger</span>
                  </div>
                  <span className="text-[11px] text-amber-400 bg-amber-950/80 px-2 py-0.5 rounded border border-amber-800">
                    REST API SDK
                  </span>
                </div>
                <pre className="text-slate-300 overflow-x-auto leading-relaxed whitespace-pre-wrap">
{`curl -X POST https://memoryvault.app/api/integrations/webhook \\
  -H "Authorization: Bearer mv_live_sec_****************" \\
  -H "Content-Type: application/json" \\
  -d '{
    "url": "https://github.com/confluentinc/schema-registry",
    "emotionalContext": "Deep Study",
    "tags": ["Kafka", "Streaming", "SchemaRegistry"],
    "priority": 9.5
  }'

# Response 202 Accepted:
# {"status":"ENQUEUED","enrichmentJobId":"job_98412"}`}
                </pre>
              </div>
              <div className="mt-4 pt-3 border-t border-slate-800/80 text-[11px] text-slate-400 flex items-center justify-between">
                <span>Works with bash, iOS Shortcuts, Python, Raycast</span>
                <span className="text-amber-400 font-semibold">Instant Ingestion</span>
              </div>
            </div>
          </div>

          {/* Bottom Callout Banner */}
          <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-purple-950 rounded-2xl p-6 sm:p-8 text-white flex flex-col md:flex-row items-center justify-between gap-6 shadow-md">
            <div className="space-y-1 text-center md:text-left">
              <div className="inline-flex items-center gap-2 text-purple-300 text-xs font-semibold uppercase tracking-wider mb-1">
                <Puzzle className="w-4 h-4 text-purple-400" />
                <span>Extensible Architecture</span>
              </div>
              <h3 className="text-xl sm:text-2xl font-bold">Build or Configure Plugins for Your Workflow</h3>
              <p className="text-xs sm:text-sm text-slate-300 max-w-xl">
                Access real-time sync telemetry, manage OAuth tokens, and test plugin connections directly inside the
                interactive Plugin Hub.
              </p>
            </div>
            <Link
              to="/plugins"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl font-semibold bg-purple-500 hover:bg-purple-400 text-white text-sm shadow-md shadow-purple-500/25 transition-all shrink-0"
            >
              Open Plugin Hub & Marketplace <ArrowRight className="w-4 h-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* ── SECTION: EXTENSION SPOTLIGHT ──────────────────────────────────── */}
      <section className="py-16 sm:py-20 bg-slate-900 text-white relative overflow-hidden">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 relative z-10 flex flex-col md:flex-row items-center justify-between gap-8">
          <div className="max-w-xl space-y-4">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-teal-950 border border-teal-700/60 text-teal-300 text-xs font-semibold">
              <Download className="w-3.5 h-3.5" />
              <span>Available for Chrome, Brave, Edge & Opera</span>
            </div>
            <h3 className="text-2xl sm:text-4xl font-extrabold tracking-tight">
              Get the Chrome Extension. Save links in 1 second.
            </h3>
            <p className="text-sm sm:text-base text-slate-300 leading-relaxed">
              Install the lightweight extension package directly into your browser. Features 1-click floating save
              buttons on any webpage and contextual search overlays directly on Google & DuckDuckGo.
            </p>
            <div className="flex flex-wrap gap-3 pt-2">
              <Link
                to="/extension"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold bg-teal-500 hover:bg-teal-400 text-slate-950 text-sm transition-all shadow-md shadow-teal-500/20"
              >
                View 2-Minute Install Guide <ArrowRight className="w-4 h-4" />
              </Link>
              <a
                href="/MemoryVault-Extension.zip"
                download="MemoryVault-Extension.zip"
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl font-semibold bg-slate-800 hover:bg-slate-700 text-white text-sm border border-slate-700 transition-all"
              >
                <Download className="w-4 h-4 text-teal-400" />
                Direct .ZIP Download
              </a>
            </div>
          </div>

          <div className="bg-slate-800/80 border border-slate-700 rounded-2xl p-6 max-w-sm w-full space-y-4 shadow-xl">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-teal-500/20 text-teal-400 flex items-center justify-center">
                <Brain className="w-6 h-6" />
              </div>
              <div>
                <h4 className="font-bold text-white text-sm">MemoryVault Extension</h4>
                <span className="text-xs text-slate-400">Manifest V3 · Local & Private</span>
              </div>
            </div>
            <ul className="text-xs text-slate-300 space-y-2 border-t border-slate-700/80 pt-3">
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-teal-400" />
                Contextual search overlay on DuckDuckGo
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-teal-400" />
                Zero tracking — 100% private
              </li>
              <li className="flex items-center gap-2">
                <CheckCircle2 className="w-3.5 h-3.5 text-teal-400" />
                Instant token sync with web app
              </li>
            </ul>
          </div>
        </div>
      </section>

      {/* ── FOOTER ────────────────────────────────────────────────────────── */}
      <footer className="bg-white border-t border-slate-200 py-12 px-4 sm:px-6">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6 text-sm text-slate-500">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded-lg bg-teal-600 flex items-center justify-center">
              <Brain className="w-4 h-4 text-white" />
            </div>
            <span className="font-bold text-slate-900">MemoryVault</span>
            <span className="text-xs text-slate-400">· Turn saved links into an active knowledge engine</span>
          </div>

          <div className="flex flex-wrap items-center gap-6 text-xs sm:text-sm">
            <Link to="/login" className="hover:text-teal-600 transition-colors">
              Sign In
            </Link>
            <Link to="/register" className="hover:text-teal-600 transition-colors">
              Create Account
            </Link>
            <Link to="/extension" className="hover:text-teal-600 transition-colors">
              Chrome Extension
            </Link>
            <Link to="/plugins" className="hover:text-teal-600 transition-colors">
              Plugin Hub
            </Link>
            <a
              href="https://github.com/Tangella-Manoj/memoryvault"
              target="_blank"
              rel="noreferrer"
              className="hover:text-teal-600 transition-colors inline-flex items-center gap-1 font-semibold"
            >
              GitHub Repo <ExternalLink className="w-3 h-3" />
            </a>
          </div>
        </div>

        <div className="max-w-6xl mx-auto mt-8 pt-6 border-t border-slate-100 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs text-slate-400">
          <p>© {new Date().getFullYear()} MemoryVault. Built with Spring Boot 3, React, and Open-Source AI.</p>
          <p>Created by Tangella Manoj · Backend & Distributed Systems Engineer</p>
        </div>
      </footer>
    </div>
  )
}
