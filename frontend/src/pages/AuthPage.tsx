import { Loader2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Logo from '../components/Logo'
import { useAuth } from '../lib/auth'

export default function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const { login, signup } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (mode === 'login') await login(email, password)
      else await signup(name, email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  const isLogin = mode === 'login'
  return (
    <div className="hv-glow flex min-h-full items-center justify-center px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center"><Logo /></div>
        <form onSubmit={submit} className="space-y-4 rounded-2xl border border-white/10 bg-ink-900/80 p-7 shadow-2xl backdrop-blur">
          <div>
            <h1 className="text-2xl font-semibold">{isLogin ? 'Welcome back' : 'Create your account'}</h1>
            <p className="mt-1 text-sm text-zinc-400">
              {isLogin ? 'Log in to keep building.' : 'Start building apps by chatting.'}
            </p>
          </div>

          {!isLogin && (
            <label className="block text-sm">
              <span className="text-zinc-300">Name</span>
              <input required value={name} onChange={(e) => setName(e.target.value)} maxLength={60}
                className="mt-1 w-full rounded-lg border border-white/10 bg-ink-800 px-3 py-2.5 outline-none focus:border-brand" />
            </label>
          )}
          <label className="block text-sm">
            <span className="text-zinc-300">Email</span>
            <input required type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)}
              className="mt-1 w-full rounded-lg border border-white/10 bg-ink-800 px-3 py-2.5 outline-none focus:border-brand" />
          </label>
          <label className="block text-sm">
            <span className="text-zinc-300">Password</span>
            <input required type="password" minLength={isLogin ? 1 : 6} autoComplete={isLogin ? 'current-password' : 'new-password'}
              value={password} onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-lg border border-white/10 bg-ink-800 px-3 py-2.5 outline-none focus:border-brand" />
          </label>

          {error && <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>}

          <button type="submit" disabled={busy}
            className="flex w-full items-center justify-center gap-2 rounded-lg bg-gradient-to-r from-brand to-brand-2 py-2.5 font-medium text-white shadow-lg shadow-brand/20 disabled:opacity-60">
            {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
            {isLogin ? 'Log in' : 'Sign up'}
          </button>

          <p className="text-center text-sm text-zinc-400">
            {isLogin ? 'New to HighVibe? ' : 'Already have an account? '}
            <Link to={isLogin ? '/signup' : '/login'} state={location.state} className="text-violet-300 hover:text-violet-200">
              {isLogin ? 'Create an account' : 'Log in'}
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
