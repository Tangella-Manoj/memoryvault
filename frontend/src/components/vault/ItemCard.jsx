import { ExternalLink } from 'lucide-react'

const CONTENT_TYPE_COLORS = {
  ARTICLE: 'bg-blue-50 text-blue-700',
  VIDEO: 'bg-red-50 text-red-700',
  TWEET: 'bg-sky-50 text-sky-700',
  THREAD: 'bg-sky-50 text-sky-700',
  REPO: 'bg-slate-100 text-slate-700',
  PRODUCT: 'bg-amber-50 text-amber-700',
  DOCUMENT: 'bg-purple-50 text-purple-700',
  IMAGE: 'bg-pink-50 text-pink-700',
  OTHER: 'bg-slate-100 text-slate-600',
}

export default function ItemCard({ item, reason }) {
  const badgeClass = CONTENT_TYPE_COLORS[item.contentType] ?? CONTENT_TYPE_COLORS.OTHER

  return (
    <a
      href={item.url}
      target="_blank"
      rel="noreferrer"
      className="block bg-white border border-slate-200 rounded-lg overflow-hidden hover:shadow-md transition-shadow"
    >
      {item.ogImageUrl && (
        <img src={item.ogImageUrl} alt="" className="w-full h-36 object-cover" />
      )}
      <div className="p-4">
        <div className="flex items-center gap-2 mb-2">
          <span className={`text-xs font-medium px-2 py-0.5 rounded ${badgeClass}`}>
            {item.contentType}
          </span>
          {item.status === 'PROCESSING' && (
            <span className="text-xs text-slate-400">Processing…</span>
          )}
        </div>
        <h3 className="font-medium text-slate-900 line-clamp-2 flex items-start gap-1">
          {item.title ?? item.url}
          <ExternalLink className="w-3.5 h-3.5 text-slate-400 shrink-0 mt-1" />
        </h3>
        {item.summary && (
          <p className="text-sm text-slate-500 mt-1 line-clamp-2">{item.summary}</p>
        )}
        {reason && (
          <p className="text-xs text-teal-700 bg-teal-50 rounded px-2 py-1 mt-3">{reason}</p>
        )}
      </div>
    </a>
  )
}
