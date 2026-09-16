import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'react-hot-toast'
import Login from '../pages/Login'
import Register from '../pages/Register'
import ForgotPassword from '../pages/ForgotPassword'
import ExtensionGuide from '../pages/ExtensionGuide'
import Dashboard from '../pages/Dashboard'
import Vault from '../pages/Vault'
import Rediscovery from '../pages/Rediscovery'
import Analytics from '../pages/Analytics'
import Profile from '../pages/Profile'
import ShareTarget from '../pages/ShareTarget'
import ProtectedLayout from '../components/shared/ProtectedLayout'

export default function AppRouter() {
  return (
    <BrowserRouter>
      <Toaster position="top-right" />
      <Routes>
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
          <Route path="/profile" element={<Profile />} />
        </Route>

        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
