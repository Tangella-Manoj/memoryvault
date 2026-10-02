import { lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import ProtectedLayout from '../components/shared/ProtectedLayout'

// Eagerly load auth pages (immediate access needed, small)
import Login from '../pages/Login'
import Register from '../pages/Register'
import ForgotPassword from '../pages/ForgotPassword'

// Lazy-load all other pages — reduces initial bundle ~50-60%
const Landing = lazy(() => import('../pages/Landing'))
const ExtensionGuide = lazy(() => import('../pages/ExtensionGuide'))
const Dashboard = lazy(() => import('../pages/Dashboard'))
const Vault = lazy(() => import('../pages/Vault'))
const Rediscovery = lazy(() => import('../pages/Rediscovery'))
const Analytics = lazy(() => import('../pages/Analytics'))
const Plugins = lazy(() => import('../pages/Plugins'))
const Profile = lazy(() => import('../pages/Profile'))
const ShareTarget = lazy(() => import('../pages/ShareTarget'))

function PageLoader() {
  return (
    <div className="min-h-screen flex items-center justify-center">
      <div className="w-6 h-6 border-2 border-teal-500 border-t-transparent rounded-full animate-spin" />
    </div>
  )
}

export default function AppRouter() {
  return (
    <BrowserRouter basename={import.meta.env.BASE_URL}>
      <Toaster position="top-right" />
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route path="/" element={<Landing />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/extension" element={<ExtensionGuide />} />
          <Route path="/download-extension" element={<Navigate to="/extension" replace />} />
          <Route path="/share-target" element={<ShareTarget />} />

          <Route element={<ProtectedLayout />}>
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/vault" element={<Vault />} />
            <Route path="/rediscovery" element={<Rediscovery />} />
            <Route path="/analytics" element={<Analytics />} />
            <Route path="/plugins" element={<Plugins />} />
            <Route path="/profile" element={<Profile />} />
          </Route>

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
