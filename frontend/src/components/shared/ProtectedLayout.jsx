import { Outlet } from 'react-router-dom'
import { useAuthGuard } from '../../hooks/useAuthGuard'
import Navbar from './Navbar'
import FloatingSaveButton from '../vault/FloatingSaveButton'
import SaveItemModal from '../vault/SaveItemModal'
import NotificationPermissionModal from '../notifications/NotificationPermissionModal'

export default function ProtectedLayout() {
  const { isAuthenticated, isRestoring } = useAuthGuard()

  if (isRestoring) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center">
        <div className="flex flex-col items-center gap-3">
          <div className="w-8 h-8 border-3 border-teal-600 border-t-transparent rounded-full animate-spin" />
          <p className="text-sm text-slate-500 font-medium">Restoring session…</p>
        </div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return null
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <main className="max-w-6xl mx-auto px-6 py-8">
        <Outlet />
      </main>
      <FloatingSaveButton />
      <SaveItemModal />
      <NotificationPermissionModal />
    </div>
  )
}
