export default function TagCloud({ topTags }) {
  if (!topTags?.length) {
    return <p className="text-sm text-slate-400">No tags yet — tags appear once items finish processing.</p>
  }

  const max = Math.max(...topTags.map((t) => t.count))

  return (
    <div className="flex flex-wrap gap-2">
      {topTags.map((tag) => {
        const scale = 0.75 + (tag.count / max) * 0.75
        return (
          <span
            key={tag.tag}
            style={{ fontSize: `${scale}rem` }}
            className="px-2.5 py-1 rounded-full bg-teal-50 text-teal-700 font-medium"
          >
            {tag.tag}
          </span>
        )
      })}
    </div>
  )
}
