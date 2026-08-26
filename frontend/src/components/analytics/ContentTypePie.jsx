import { PieChart, Pie, Cell, Tooltip, ResponsiveContainer, Legend } from 'recharts'

const COLORS = ['#2d6a6a', '#5fb3ae', '#a8641f', '#e0a352', '#64748b', '#94a3b8', '#1f4d4d', '#7fcac5', '#334155']

export default function ContentTypePie({ itemsByContentType }) {
  const data = Object.entries(itemsByContentType ?? {}).map(([name, value]) => ({ name, value }))

  if (data.length === 0) {
    return <p className="text-sm text-slate-400">No items yet</p>
  }

  return (
    <ResponsiveContainer width="100%" height={240}>
      <PieChart>
        <Pie data={data} dataKey="value" nameKey="name" outerRadius={80}>
          {data.map((entry, index) => (
            <Cell key={entry.name} fill={COLORS[index % COLORS.length]} />
          ))}
        </Pie>
        <Tooltip />
        <Legend wrapperStyle={{ fontSize: 12 }} />
      </PieChart>
    </ResponsiveContainer>
  )
}
