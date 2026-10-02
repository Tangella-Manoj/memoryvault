import { useState, useEffect } from 'react'
import { Link, useSearchParams, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import toast from 'react-hot-toast'
import { Mail, Lock, KeyRound, Eye, EyeOff, ArrowLeft, CheckCircle2, AlertCircle } from 'lucide-react'
import api from '../lib/api'
import AuthLayout from '../components/shared/AuthLayout'

export default function ForgotPassword() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const initialEmail = searchParams.get('email') || ''

  const [step, setStep] = useState(1) // 1 = Request Code, 2 = Set New Password
  const [targetEmail, setTargetEmail] = useState(initialEmail)
  const [generatedCode, setGeneratedCode] = useState(null)

  const [submitting, setSubmitting] = useState(false)
  const [serverError, setServerError] = useState(null)
  const [showPassword, setShowPassword] = useState(false)

  const {
    register: regStep1,
    handleSubmit: handleStep1,
    setValue: setStep1Val,
    formState: { errors: errorsStep1 },
  } = useForm({ defaultValues: { email: initialEmail } })

  const {
    register: regStep2,
    handleSubmit: handleStep2,
    watch: watchStep2,
    setValue: setStep2Val,
    formState: { errors: errorsStep2 },
  } = useForm()

  useEffect(() => {
    if (initialEmail) {
      setStep1Val('email', initialEmail)
      setTargetEmail(initialEmail)
    }
  }, [initialEmail, setStep1Val])

  // Step 1: Request Reset Code
  async function onRequestCode(data) {
    setSubmitting(true)
    setServerError(null)
    try {
      const cleanEmail = data.email.trim()
      const res = await api.post('/auth/forgot-password', { email: cleanEmail })
      setTargetEmail(cleanEmail)

      const code = res.data?.data?.resetCode
      if (code) {
        setGeneratedCode(code)
        setStep2Val('code', code)
        toast.success(`Verification code generated: ${code}`, { duration: 6000 })
      } else {
        toast.success('Check your email for the reset code!')
      }

      setStep(2)
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to request reset code'
      setServerError(msg)
      toast.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  // Step 2: Reset Password
  async function onResetPassword(data) {
    if (data.newPassword !== data.confirmPassword) {
      toast.error('Passwords do not match')
      return
    }

    setSubmitting(true)
    setServerError(null)
    try {
      await api.post('/auth/reset-password', {
        email: targetEmail,
        token: data.code.trim(),
        newPassword: data.newPassword,
      })

      toast.success('Password reset successfully! Please sign in.')
      navigate(`/login?email=${encodeURIComponent(targetEmail)}`)
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to reset password'
      setServerError(msg)
      toast.error(msg)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title={step === 1 ? 'Reset your password' : 'Set a new password'}
      subtitle={
        step === 1
          ? 'Enter your account email to receive a password reset code'
          : `Enter the code and choose a new password for ${targetEmail}`
      }
    >
      {/* Error banner */}
      {serverError && (
        <div
          role="alert"
          className="mb-5 flex items-start gap-2.5 rounded-xl border border-red-200 bg-red-50 p-3.5 text-xs sm:text-sm text-red-800"
        >
          <AlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="font-medium">{serverError}</p>
            {serverError.toLowerCase().includes('no account found') && (
              <p className="mt-1 text-xs">
                Don't have an account yet?{' '}
                <Link to="/register" className="font-semibold underline hover:text-red-950">
                  Register here
                </Link>
              </p>
            )}
          </div>
        </div>
      )}

      {step === 1 ? (
        /* STEP 1: Enter Email */
        <form onSubmit={handleStep1(onRequestCode)} className="space-y-4" noValidate>
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Account Email Address
            </label>
            <div className="relative">
              <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                className={`w-full border rounded-xl pl-9 pr-3.5 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                  errorsStep1.email
                    ? 'border-red-300 focus:ring-red-400 bg-red-50/30'
                    : 'border-slate-300 focus:ring-teal-500 bg-white'
                }`}
                {...regStep1('email', {
                  required: 'Email is required',
                  pattern: {
                    value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                    message: 'Enter a valid email address',
                  },
                })}
              />
            </div>
            {errorsStep1.email && (
              <p className="text-xs text-red-600 mt-1 font-medium">{errorsStep1.email.message}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-teal-600 text-white rounded-xl py-2.5 text-sm font-semibold hover:bg-teal-700 disabled:opacity-50 transition-all shadow-md shadow-teal-600/20 cursor-pointer"
          >
            {submitting ? 'Generating Code…' : 'Send Reset Code'}
          </button>
        </form>
      ) : (
        /* STEP 2: Enter Code & New Password */
        <form onSubmit={handleStep2(onResetPassword)} className="space-y-4" noValidate>
          {generatedCode ? (
            <div className="rounded-xl border border-teal-200 bg-teal-50/80 p-3.5 text-xs text-teal-900 flex items-start gap-2.5">
              <KeyRound className="w-4 h-4 text-teal-600 shrink-0 mt-0.5" />
              <div className="flex-1">
                <p className="font-semibold text-teal-950">One-Time Verification Code</p>
                <div className="mt-1.5 flex items-center gap-2">
                  <span className="font-mono text-base font-bold tracking-widest text-slate-900 bg-white px-2.5 py-0.5 rounded border border-teal-300 shadow-sm">
                    {generatedCode}
                  </span>
                  <span className="text-[11px] text-teal-700 font-medium">✓ Auto-filled below</span>
                </div>
                <p className="mt-1.5 text-[11px] text-teal-800 leading-relaxed">
                  Outbound email is not wired to an external SMTP server on this host. Your 6-digit code has been generated and pre-filled for immediate reset.
                </p>
              </div>
            </div>
          ) : (
            <div className="rounded-xl border border-blue-200 bg-blue-50 p-3.5 text-xs text-blue-900 flex items-start gap-2.5">
              <CheckCircle2 className="w-4 h-4 text-blue-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Reset code sent!</p>
                <p className="mt-0.5 text-xs">Check your email at <span className="font-mono font-medium">{targetEmail}</span> for a 6-digit code. It expires in 15 minutes.</p>
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Verification Code (6 Digits)
            </label>
            <div className="relative">
              <KeyRound className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                placeholder="123456"
                maxLength={10}
                className={`w-full border rounded-xl pl-9 pr-3.5 py-2.5 text-sm font-mono tracking-wider transition-all focus:outline-none focus:ring-2 ${
                  errorsStep2.code
                    ? 'border-red-300 focus:ring-red-400 bg-red-50/30'
                    : 'border-slate-300 focus:ring-teal-500 bg-white'
                }`}
                {...regStep2('code', { required: 'Verification code is required' })}
              />
            </div>
            {errorsStep2.code && (
              <p className="text-xs text-red-600 mt-1 font-medium">{errorsStep2.code.message}</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              New Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="At least 8 characters"
                className={`w-full border rounded-xl pl-9 pr-10 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                  errorsStep2.newPassword
                    ? 'border-red-300 focus:ring-red-400 bg-red-50/30'
                    : 'border-slate-300 focus:ring-teal-500 bg-white'
                }`}
                {...regStep2('newPassword', {
                  required: 'New password is required',
                  minLength: { value: 8, message: 'Password must be at least 8 characters' },
                })}
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
              >
                {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
            {errorsStep2.newPassword && (
              <p className="text-xs text-red-600 mt-1 font-medium">{errorsStep2.newPassword.message}</p>
            )}
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-700 mb-1.5">
              Confirm New Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
              <input
                type={showPassword ? 'text' : 'password'}
                autoComplete="new-password"
                placeholder="Re-type your password"
                className={`w-full border rounded-xl pl-9 pr-3.5 py-2.5 text-sm transition-all focus:outline-none focus:ring-2 ${
                  errorsStep2.confirmPassword
                    ? 'border-red-300 focus:ring-red-400 bg-red-50/30'
                    : 'border-slate-300 focus:ring-teal-500 bg-white'
                }`}
                {...regStep2('confirmPassword', {
                  required: 'Please confirm your new password',
                  validate: (val) => val === watchStep2('newPassword') || 'Passwords do not match',
                })}
              />
            </div>
            {errorsStep2.confirmPassword && (
              <p className="text-xs text-red-600 mt-1 font-medium">{errorsStep2.confirmPassword.message}</p>
            )}
          </div>

          <div className="flex gap-2.5 pt-1">
            <button
              type="button"
              onClick={() => setStep(1)}
              className="px-4 py-2.5 text-sm font-medium border border-slate-300 hover:bg-slate-50 text-slate-700 rounded-xl transition-colors cursor-pointer"
            >
              Back
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="flex-1 bg-teal-600 text-white rounded-xl py-2.5 text-sm font-semibold hover:bg-teal-700 disabled:opacity-50 transition-all shadow-md shadow-teal-600/20 cursor-pointer"
            >
              {submitting ? 'Resetting Password…' : 'Save New Password'}
            </button>
          </div>
        </form>
      )}

      <div className="mt-6 pt-5 border-t border-slate-200/70 text-center">
        <Link
          to="/login"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-slate-600 hover:text-teal-700 transition-colors"
        >
          <ArrowLeft className="w-3.5 h-3.5" />
          <span>Back to sign in</span>
        </Link>
      </div>
    </AuthLayout>
  )
}
