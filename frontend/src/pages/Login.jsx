import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import { Mail, Lock, Eye, EyeOff, AlertCircle, ArrowRight, Download } from 'lucide-react'
import api from '../lib/api'
import { useAuthStore } from '../stores/authStore'
import AuthLayout from '../components/shared/AuthLayout'

export default function Login() {
  const [searchParams] = useSearchParams()
  const initialEmail = searchParams.get('email') || ''

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    setError,
    clearErrors,
    formState: { errors },
  } = useForm({ defaultValues: { email: initialEmail, password: '' } })

  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState(null)
  const [showPassword, setShowPassword] = useState(false)

  const login = useAuthStore((s) => s.login)
  const accessToken = useAuthStore((s) => s.accessToken)
  const navigate = useNavigate()

  const currentEmail = watch('email') || ''

  useEffect(() => {
    if (accessToken) navigate('/dashboard', { replace: true })
  }, [accessToken, navigate])

  useEffect(() => {
    if (initialEmail) {
      setValue('email', initialEmail)
    }
  }, [initialEmail, setValue])

  async function onSubmit(data) {
    setSubmitting(true)
    setServerError(null)
    clearErrors()

    try {
      const response = await api.post('/auth/login', {
        email: data.email.trim(),
        password: data.password,
      })
      login(response.data.data)
      toast.success('Welcome back!')
      navigate('/dashboard')
    } catch (err) {
      const message =
        err.response?.data?.message ||
        (err.message === 'Network Error'
          ? 'Cannot connect to server. Please check your network connection.'
          : err.message || 'Login failed. Please check your credentials.')
      setServerError(message)
      toast.error(message)

      const lower = message.toLowerCase()
      if (
        lower.includes('no account found') ||
        lower.includes('user not found') ||
        lower.includes('not registered') ||
        lower.includes('email')
      ) {
        setError('email', { type: 'server', message })
      } else if (
        lower.includes('incorrect password') ||
        lower.includes('invalid password') ||
        lower.includes('password')
      ) {
        setError('password', { type: 'server', message })
      }
    } finally {
      setSubmitting(false)
    }
  }

  function handleInputChange() {
    if (serverError) setServerError(null)
  }

  const isUserNotFound =
    serverError?.toLowerCase().includes('no account found') ||
    serverError?.toLowerCase().includes('user not found') ||
    serverError?.toLowerCase().includes('not registered')
  const isWrongPassword =
    serverError?.toLowerCase().includes('incorrect password') ||
    serverError?.toLowerCase().includes('invalid password') ||
    serverError?.toLowerCase().includes('wrong password')

  return (
    <AuthLayout title="Sign in to MemoryVault" subtitle="Access your second brain and saved knowledge">
      {/* Exact Error Alert Banner */}
      {serverError && (
        <div
          role="alert"
          className="mb-5 rounded-xl border border-red-200 bg-red-50 p-4 text-xs sm:text-sm text-red-900"
        >
          <div className="flex items-start gap-2.5">
            <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-semibold">{serverError}</p>

              {/* Smart Contextual Recovery Actions */}
              {isUserNotFound && (
                <div className="mt-2 pt-2 border-t border-red-200/60 flex items-center gap-1.5">
                  <span className="text-red-700">Need an account?</span>
                  <Link
                    to={`/register?email=${encodeURIComponent(currentEmail.trim())}`}
                    className="font-bold text-teal-800 hover:text-teal-950 inline-flex items-center gap-1 underline"
                  >
                    <span>Create one now</span>
                    <ArrowRight className="w-3 h-3" />
                  </Link>
                </div>
              )}

              {isWrongPassword && (
                <div className="mt-2 pt-2 border-t border-red-200/60 flex items-center gap-1.5">
                  <span className="text-red-700">Forgot your password?</span>
                  <Link
                    to={`/forgot-password?email=${encodeURIComponent(currentEmail.trim())}`}
                    className="font-bold text-teal-800 hover:text-teal-950 inline-flex items-center gap-1 underline"
                  >
                    <span>Reset it here</span>
                    <ArrowRight className="w-3 h-3" />
                  </Link>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* Email Field */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
            Email Address
          </label>
          <div className="relative">
            <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="email"
              autoComplete="email"
              placeholder="you@example.com"
              className={`w-full border rounded-xl pl-10 pr-3.5 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                errors.email
                  ? 'border-red-300 focus:ring-red-400 bg-red-50/20'
                  : 'border-slate-300 focus:ring-teal-500 bg-white'
              }`}
              {...register('email', {
                required: 'Email is required',
                onChange: handleInputChange,
              })}
            />
          </div>
          {errors.email && <p className="text-xs text-red-600 mt-1 font-medium">{errors.email.message}</p>}
        </div>

        {/* Password Field */}
        <div>
          <div className="flex items-center justify-between mb-1.5">
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700">
              Password
            </label>
            <Link
              to={currentEmail ? `/forgot-password?email=${encodeURIComponent(currentEmail.trim())}` : '/forgot-password'}
              className="text-xs font-semibold text-teal-600 hover:text-teal-700 hover:underline transition-colors"
            >
              Forgot password?
            </Link>
          </div>
          <div className="relative">
            <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              placeholder="••••••••"
              className={`w-full border rounded-xl pl-10 pr-10 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                errors.password
                  ? 'border-red-300 focus:ring-red-400 bg-red-50/20'
                  : 'border-slate-300 focus:ring-teal-500 bg-white'
              }`}
              {...register('password', {
                required: 'Password is required',
                onChange: handleInputChange,
              })}
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 cursor-pointer"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
          {errors.password && (
            <p className="text-xs text-red-600 mt-1 font-medium">{errors.password.message}</p>
          )}
        </div>

        {/* Submit Button */}
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-teal-600 text-white rounded-xl py-2.5 text-sm font-semibold hover:bg-teal-700 disabled:opacity-50 transition-all shadow-md shadow-teal-600/20 cursor-pointer flex items-center justify-center gap-2 mt-2"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      {/* Footer Switch */}
      <div className="mt-6 pt-5 border-t border-slate-200/70 text-center">
        <p className="text-sm text-slate-600">
          Don't have an account?{' '}
          <Link
            to={currentEmail ? `/register?email=${encodeURIComponent(currentEmail.trim())}` : '/register'}
            className="font-semibold text-teal-600 hover:text-teal-700 underline"
          >
            Create one free
          </Link>
        </p>
      </div>

      {/* Extension Promo Card */}
      <div className="mt-5 rounded-xl bg-slate-50 border border-slate-200/80 p-3.5 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2.5 min-w-0">
          <div className="w-7 h-7 rounded-lg bg-teal-100 text-teal-700 flex items-center justify-center shrink-0">
            <Download className="w-3.5 h-3.5" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-semibold text-slate-900 truncate">MemoryVault Extension</p>
            <p className="text-[11px] text-slate-500 truncate">1-click saving on any web page</p>
          </div>
        </div>
        <Link
          to="/extension"
          className="shrink-0 text-xs font-semibold text-teal-700 hover:text-teal-800 bg-white border border-slate-200 px-2.5 py-1 rounded-lg shadow-2xs hover:bg-slate-50 transition-colors"
        >
          Get Extension
        </Link>
      </div>
    </AuthLayout>
  )
}
