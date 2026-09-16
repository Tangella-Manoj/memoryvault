import { useState, useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import toast from 'react-hot-toast'
import {
  User,
  Mail,
  Lock,
  Eye,
  EyeOff,
  AlertCircle,
  ArrowRight,
  KeyRound,
  Download,
} from 'lucide-react'
import api from '../lib/api'
import { useAuthStore } from '../stores/authStore'
import AuthLayout from '../components/shared/AuthLayout'

export default function Register() {
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
  } = useForm({ defaultValues: { displayName: '', email: initialEmail, password: '' } })

  const [submitting, setSubmitting] = useState(false)
  const [duplicateUser, setDuplicateUser] = useState(null) // stores { email: string } when user already exists
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
    setDuplicateUser(null)
    clearErrors()

    try {
      const response = await api.post('/auth/register', {
        displayName: data.displayName.trim(),
        email: data.email.trim(),
        password: data.password,
      })
      login(response.data.data)
      toast.success('Account created! Welcome to MemoryVault.')
      navigate('/dashboard')
    } catch (err) {
      const message = err.response?.data?.message || 'Registration failed. Please try again.'
      const isDuplicate =
        message.toLowerCase().includes('already exists') ||
        message.toLowerCase().includes('already registered')

      if (isDuplicate) {
        setDuplicateUser({ email: data.email.trim() })
        setError('email', {
          type: 'server',
          message: 'This email is already associated with an account.',
        })
        toast.error('An account with this email already exists.')
      } else {
        setServerError(message)
        toast.error(message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  function handleInputChange() {
    if (serverError) setServerError(null)
    if (duplicateUser) setDuplicateUser(null)
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Start capturing and resurfacing knowledge with AI"
    >
      {/* Friendly "Account Already Exists" Recovery Banner */}
      {duplicateUser && (
        <div
          role="alert"
          className="mb-5 rounded-xl border border-amber-300 bg-amber-50 p-4 text-xs sm:text-sm text-amber-950 shadow-xs"
        >
          <div className="flex items-start gap-2.5">
            <AlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="font-bold text-slate-900 text-sm">Account Already Exists</p>
              <p className="mt-1 text-slate-700 leading-relaxed">
                An account with <strong className="font-semibold text-slate-900">{duplicateUser.email}</strong> is
                already registered.
              </p>

              <div className="mt-3.5 flex flex-wrap items-center gap-2 pt-2 border-t border-amber-200/80">
                <Link
                  to={`/login?email=${encodeURIComponent(duplicateUser.email)}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-teal-600 text-white hover:bg-teal-700 shadow-xs transition-colors"
                >
                  <span>Sign In with this email</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </Link>

                <Link
                  to={`/forgot-password?email=${encodeURIComponent(duplicateUser.email)}`}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-700 bg-white hover:bg-slate-50 border border-amber-300/80 transition-colors"
                >
                  <KeyRound className="w-3 h-3 text-slate-500" />
                  <span>Forgot password?</span>
                </Link>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* General server error */}
      {serverError && (
        <div
          role="alert"
          className="mb-5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs sm:text-sm text-red-900 flex items-start gap-2.5"
        >
          <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
          <p className="font-medium flex-1">{serverError}</p>
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {/* Full Name */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
            Your Name
          </label>
          <div className="relative">
            <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              autoComplete="name"
              placeholder="Jane Doe"
              className={`w-full border rounded-xl pl-10 pr-3.5 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                errors.displayName
                  ? 'border-red-300 focus:ring-red-400 bg-red-50/20'
                  : 'border-slate-300 focus:ring-teal-500 bg-white'
              }`}
              {...register('displayName', {
                required: 'Name is required',
                minLength: { value: 2, message: 'At least 2 characters' },
                onChange: handleInputChange,
              })}
            />
          </div>
          {errors.displayName && (
            <p className="text-xs text-red-600 mt-1 font-medium">{errors.displayName.message}</p>
          )}
        </div>

        {/* Email Address */}
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
                errors.email || duplicateUser
                  ? 'border-amber-300 focus:ring-amber-400 bg-amber-50/20'
                  : 'border-slate-300 focus:ring-teal-500 bg-white'
              }`}
              {...register('email', {
                required: 'Email is required',
                pattern: {
                  value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                  message: 'Enter a valid email address',
                },
                onChange: handleInputChange,
              })}
            />
          </div>
          {errors.email && <p className="text-xs text-red-600 mt-1 font-medium">{errors.email.message}</p>}
        </div>

        {/* Password */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
            Password
          </label>
          <div className="relative">
            <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              placeholder="Minimum 8 characters"
              className={`w-full border rounded-xl pl-10 pr-10 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                errors.password
                  ? 'border-red-300 focus:ring-red-400 bg-red-50/20'
                  : 'border-slate-300 focus:ring-teal-500 bg-white'
              }`}
              {...register('password', {
                required: 'Password is required',
                minLength: { value: 8, message: 'At least 8 characters' },
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

        {/* Submit */}
        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-teal-600 text-white rounded-xl py-2.5 text-sm font-semibold hover:bg-teal-700 disabled:opacity-50 transition-all shadow-md shadow-teal-600/20 cursor-pointer flex items-center justify-center gap-2 mt-2"
        >
          {submitting ? 'Creating account…' : 'Create free account'}
        </button>
      </form>

      {/* Footer Switch */}
      <div className="mt-6 pt-5 border-t border-slate-200/70 text-center">
        <p className="text-sm text-slate-600">
          Already have an account?{' '}
          <Link
            to={currentEmail ? `/login?email=${encodeURIComponent(currentEmail.trim())}` : '/login'}
            className="font-semibold text-teal-600 hover:text-teal-700 underline"
          >
            Sign in
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
            <p className="text-xs font-semibold text-slate-900 truncate">Chrome Extension</p>
            <p className="text-[11px] text-slate-500 truncate">Download package without login</p>
          </div>
        </div>
        <Link
          to="/extension"
          className="shrink-0 text-xs font-semibold text-teal-700 hover:text-teal-800 bg-white border border-slate-200 px-2.5 py-1 rounded-lg shadow-2xs hover:bg-slate-50 transition-colors"
        >
          Download Guide
        </Link>
      </div>
    </AuthLayout>
  )
}
