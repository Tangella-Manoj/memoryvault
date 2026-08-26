const CONTENT_TYPES = ['ARTICLE', 'VIDEO', 'TWEET', 'THREAD', 'PRODUCT', 'REPO', 'DOCUMENT', 'IMAGE', 'OTHER']

export default function VaultFilters({ contentType, onContentTypeChange }) {
  return (
    <div className="flex items-center gap-2 flex-wrap">
      <button
        onClick={() => onContentTypeChange('')}
        className={`text-xs px-3 py-1.5 rounded-full border ${
          contentType === '' ? 'bg-teal-600 text-white border-teal-600' : 'border-slate-300 text-slate-600'
        }`}
      >
        All
      </button>
      {CONTENT_TYPES.map((type) => (
        <button
          key={type}
          onClick={() => onContentTypeChange(type)}
          className={`text-xs px-3 py-1.5 rounded-full border ${
            contentType === type ? 'bg-teal-600 text-white border-teal-600' : 'border-slate-300 text-slate-600'
          }`}
        >
          {type}
        </button>
      ))}
    </div>
  )
}
