export default function MetricCard({ label, value, icon: Icon }) {
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-4">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-slate-500">{label}</span>
        {Icon && <Icon className="w-4 h-4 text-teal-600" />}
      </div>
      <div className="text-2xl font-semibold text-slate-900">{value}</div>
    </div>
  )
}
