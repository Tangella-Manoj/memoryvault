import ItemCard from '../vault/ItemCard'

export default function ResurfaceFeed({ items }) {
  if (!items?.length) {
    return (
      <div className="bg-white border border-slate-200 rounded-lg p-8 text-center text-sm text-slate-500">
        Save a few more items to unlock personalized resurfacing.
      </div>
    )
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
      {items.map((result) => (
        <ItemCard key={result.item.id} item={result.item} reason={result.reason} />
      ))}
    </div>
  )
}
