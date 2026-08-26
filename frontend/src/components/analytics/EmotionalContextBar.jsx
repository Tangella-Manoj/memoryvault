import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts'

export default function EmotionalContextBar({ itemsByEmotionalContext }) {
  const data = Object.entries(itemsByEmotionalContext ?? {}).map(([name, value]) => ({ name, value }))

  if (data.length === 0) {
    return <p className="text-sm text-slate-400">No items yet</p>
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <BarChart data={data} layout="vertical" margin={{ left: 20 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" horizontal={false} />
        <XAxis type="number" allowDecimals={false} tick={{ fontSize: 12 }} stroke="#94a3b8" />
        <YAxis type="category" dataKey="name" tick={{ fontSize: 12 }} stroke="#94a3b8" width={90} />
        <Tooltip />
        <Bar dataKey="value" fill="#5fb3ae" radius={[0, 4, 4, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
