import { useEffect, useState } from 'react'
import { Search } from 'lucide-react'
import api from '../lib/api'
import ItemCard from '../components/vault/ItemCard'
import VaultFilters from '../components/vault/VaultFilters'
import { useSearch } from '../hooks/useSearch'

export default function Vault() {
  const [page, setPage] = useState(0)
  const [paged, setPaged] = useState(null)
  const [contentType, setContentType] = useState('')
  const [query, setQuery] = useState('')
  const { results: searchResults, loading: searching, search } = useSearch()

  useEffect(() => {
    api.get('/vault', { params: { page, size: 24 } }).then((res) => setPaged(res.data.data))
  }, [page])

  function handleSearchSubmit(e) {
    e.preventDefault()
    search(query)
  }

  const isSearching = query.trim().length > 0
  const items = isSearching
    ? searchResults.map((r) => r.item)
    : (paged?.content ?? []).filter((i) => !contentType || i.contentType === contentType)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Vault</h1>
        <p className="text-sm text-slate-500 mt-1">Everything you've saved, searchable by meaning, not just keywords.</p>
      </div>

      <form onSubmit={handleSearchSubmit} className="relative max-w-xl">
        <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search by mood, topic, or memory…"
          className="w-full border border-slate-300 rounded-md pl-9 pr-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
        />
      </form>

      {!isSearching && (
        <VaultFilters contentType={contentType} onContentTypeChange={setContentType} />
      )}

      {searching && <p className="text-sm text-slate-500">Searching…</p>}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        {items.map((item) => (
          <ItemCard
            key={item.id}
            item={item}
            reason={isSearching ? searchResults.find((r) => r.item.id === item.id)?.reason : undefined}
          />
        ))}
      </div>

      {!isSearching && paged && paged.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 pt-4">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            className="text-sm px-3 py-1.5 rounded-md border border-slate-300 disabled:opacity-40"
          >
            Previous
          </button>
          <span className="text-sm text-slate-500">
            Page {page + 1} of {paged.totalPages}
          </span>
          <button
            disabled={page + 1 >= paged.totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="text-sm px-3 py-1.5 rounded-md border border-slate-300 disabled:opacity-40"
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
