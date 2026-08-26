import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { useState, useEffect } from 'react'
import toast from 'react-hot-toast'
import { Brain } from 'lucide-react'
import api from '../lib/api'
import { useAuthStore } from '../stores/authStore'

export default function Login() {
  const { register, handleSubmit, formState: { errors } } = useForm()
  const [submitting, setSubmitting] = useState(false)
  const login = useAuthStore((s) => s.login)
  const accessToken = useAuthStore((s) => s.accessToken)
  const navigate = useNavigate()

  useEffect(() => {
    if (accessToken) navigate('/dashboard', { replace: true })
  }, [accessToken, navigate])

  async function onSubmit(data) {
    setSubmitting(true)
    try {
      const response = await api.post('/auth/login', data)
      login(response.data.data)
      navigate('/dashboard')
    } catch (err) {
      toast.error(err.response?.data?.message ?? 'Login failed')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 px-4">
      <div className="w-full max-w-sm">
        <div className="flex items-center gap-2 justify-center mb-8">
          <Brain className="w-7 h-7 text-teal-600" />
          <span className="text-xl font-semibold text-slate-900">MemoryVault</span>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="bg-white border border-slate-200 rounded-lg p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Email</label>
            <input
              type="email"
              autoComplete="email"
              className="w-full border border-slate-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
              {...register('email', { required: 'Email is required' })}
            />
            {errors.email && <p className="text-xs text-red-600 mt-1">{errors.email.message}</p>}
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-700 mb-1">Password</label>
            <input
              type="password"
              autoComplete="current-password"
              className="w-full border border-slate-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-teal-500"
              {...register('password', { required: 'Password is required' })}
            />
            {errors.password && <p className="text-xs text-red-600 mt-1">{errors.password.message}</p>}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full bg-teal-600 text-white rounded-md py-2 text-sm font-medium hover:bg-teal-700 disabled:opacity-50"
          >
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p className="text-center text-sm text-slate-500 mt-4">
          No account? <Link to="/register" className="text-teal-600 font-medium">Register</Link>
        </p>
      </div>
    </div>
  )
}
