import { Outlet } from 'react-router-dom'
import { useAuthGuard } from '../../hooks/useAuthGuard'
import Navbar from './Navbar'
import FloatingSaveButton from '../vault/FloatingSaveButton'
import SaveItemModal from '../vault/SaveItemModal'

export default function ProtectedLayout() {
  const accessToken = useAuthGuard()

  if (!accessToken) {
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
    </div>
  )
}
