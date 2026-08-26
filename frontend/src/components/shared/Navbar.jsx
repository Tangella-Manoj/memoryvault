import { Link, useNavigate } from 'react-router-dom'
import { Brain, LayoutDashboard, Library, Sparkles, BarChart3, LogOut } from 'lucide-react'
import { useAuthStore } from '../../stores/authStore'

const navItems = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/vault', label: 'Vault', icon: Library },
  { to: '/rediscovery', label: 'Rediscovery', icon: Sparkles },
  { to: '/analytics', label: 'Analytics', icon: BarChart3 },
]

export default function Navbar() {
  const user = useAuthStore((s) => s.user)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <nav className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-white">
      <Link to="/dashboard" className="flex items-center gap-2 font-semibold text-slate-900">
        <Brain className="w-5 h-5 text-teal-600" />
        MemoryVault
      </Link>

      <div className="flex items-center gap-1">
        {navItems.map(({ to, label, icon: Icon }) => (
          <Link
            key={to}
            to={to}
            className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm text-slate-600 hover:bg-slate-100 hover:text-slate-900 transition-colors"
          >
            <Icon className="w-4 h-4" />
            {label}
          </Link>
        ))}
      </div>

      <div className="flex items-center gap-3">
        <span className="text-sm text-slate-500">{user?.displayName}</span>
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 px-3 py-2 rounded-md text-sm text-slate-600 hover:bg-slate-100"
        >
          <LogOut className="w-4 h-4" />
          Log out
        </button>
      </div>
    </nav>
  )
}
