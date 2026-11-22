import { Check, Eye, EyeOff, Loader2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import Logo from '../components/Logo'
import MiniAppMock from '../components/MiniAppMock'
import { useAuth } from '../lib/auth'

const POINTS = [
  'Describe an app and watch it being written',
  'A live preview that updates as the code changes',
  'Real React code you can edit and download',
  'Invite teammates as editors or viewers',
]

const inputClass = 'mt-1.5 w-full rounded-xl border border-white/10 bg-ink-800/80 px-3.5 py-2.5 outline-none transition placeholder:text-zinc-600 focus:border-violet-400/60 focus:ring-4 focus:ring-violet-500/10'

export default function AuthPage({ mode }: { mode: 'login' | 'signup' }) {
  const { login, signup } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const isLogin = mode === 'login'

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (isLogin) await login(email, password)
      else await signup(name, email, password)
      navigate(from, { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="grid min-h-full lg:grid-cols-2">
      {/* ------------------------------------------------------------ brand panel */}
      <aside className="relative hidden overflow-hidden border-r border-white/5 lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div className="hv-aurora" aria-hidden="true" />
        <div className="hv-grid" aria-hidden="true" />
        <div className="relative"><Logo /></div>
        <div className="relative">
          <h2 className="text-4xl font-extrabold leading-tight tracking-tight">
            Turn ideas into <span className="hv-gradient-text">working apps</span>
          </h2>
          <ul className="mt-8 space-y-3">
            {POINTS.map((p) => (
              <li key={p} className="flex items-center gap-3 text-zinc-300">
                <span className="grid h-6 w-6 place-items-center rounded-full bg-emerald-400/15 text-emerald-300"><Check className="h-3.5 w-3.5" aria-hidden="true" /></span>
                {p}
              </li>
            ))}
          </ul>
          <div className="mt-10 h-52 max-w-md overflow-hidden rounded-2xl border border-white/10 shadow-2xl shadow-brand/20">
            <MiniAppMock gradient="from-violet-500/60 via-fuchsia-500/40 to-rose-500/40" variant={0} />
          </div>
        </div>
        <p className="relative text-sm text-zinc-500">Describe it. Generate it. Ship it.</p>
      </aside>

      {/* ------------------------------------------------------------ form */}
      <main className="relative flex items-center justify-center px-6 py-12">
        <div className="hv-glow absolute inset-0 lg:hidden" aria-hidden="true" />
        <div className="relative w-full max-w-sm">
          <div className="mb-10 lg:hidden"><Logo /></div>
          <h1 className="text-3xl font-bold tracking-tight">{isLogin ? 'Welcome back' : 'Create your account'}</h1>
          <p className="mt-2 text-zinc-400">{isLogin ? 'Log in to keep building.' : 'Start building apps by chatting. It is free.'}</p>

          <form onSubmit={submit} className="mt-8 space-y-4">
            {!isLogin && (
              <label className="block text-sm">
                <span className="font-medium text-zinc-300">Name</span>
                <input required value={name} onChange={(e) => setName(e.target.value)} maxLength={60} placeholder="Ada Lovelace" className={inputClass} />
              </label>
            )}
            <label className="block text-sm">
              <span className="font-medium text-zinc-300">Email</span>
              <input required type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="you@example.com" className={inputClass} />
            </label>
            <label className="block text-sm">
              <span className="font-medium text-zinc-300">Password</span>
              <span className="relative block">
                <input required type={showPassword ? 'text' : 'password'} minLength={isLogin ? 1 : 6}
                  autoComplete={isLogin ? 'current-password' : 'new-password'} value={password}
                  onChange={(e) => setPassword(e.target.value)} placeholder={isLogin ? '' : 'At least 6 characters'} className={`${inputClass} pr-11`} />
                <button type="button" onClick={() => setShowPassword((v) => !v)} aria-label={showPassword ? 'Hide password' : 'Show password'}
                  className="absolute top-1/2 right-2.5 mt-[3px] -translate-y-1/2 rounded-lg p-1.5 text-zinc-500 hover:text-zinc-200">
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </span>
            </label>

            {error && <p role="alert" className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3.5 py-2.5 text-sm text-rose-200">{error}</p>}

            <button type="submit" disabled={busy}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-brand via-fuchsia-500 to-brand-2 py-3 font-semibold text-white shadow-lg shadow-brand/25 transition hover:brightness-110 disabled:opacity-60">
              {busy && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
              {isLogin ? 'Log in' : 'Create account'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-zinc-400">
            {isLogin ? 'New to HighVibe? ' : 'Already have an account? '}
            <Link to={isLogin ? '/signup' : '/login'} state={location.state} className="font-medium text-violet-300 hover:text-violet-200">
              {isLogin ? 'Create an account' : 'Log in'}
            </Link>
          </p>
        </div>
      </main>
    </div>
  )
}
