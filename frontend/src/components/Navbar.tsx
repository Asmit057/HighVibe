import { CreditCard, LogOut } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import Logo from './Logo'

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const initials = user?.name?.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() ?? ''

  return (
    <header className="sticky top-0 z-30 border-b border-white/5 bg-ink-950/60 backdrop-blur-xl">
      <div className="mx-auto flex max-w-6xl items-center justify-between px-6 py-3.5">
        <Logo />
        <nav className="flex items-center gap-1.5 text-sm">
          {user ? (
            <>
              <Link to="/billing" className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-zinc-300 transition hover:bg-white/5 hover:text-white">
                <CreditCard className="h-4 w-4" aria-hidden="true" /> Plans
              </Link>
              <span className="ml-1 hidden items-center gap-2 rounded-full border border-white/10 bg-white/5 py-1 pr-3 pl-1 text-zinc-200 sm:flex">
                <span className="grid h-7 w-7 place-items-center rounded-full bg-gradient-to-br from-brand to-brand-2 text-xs font-bold text-white">{initials}</span>
                {user.name}
              </span>
              <button
                type="button"
                onClick={async () => {
                  await logout()
                  navigate('/')
                }}
                className="rounded-lg p-2 text-zinc-400 transition hover:bg-white/5 hover:text-white"
                aria-label="Sign out"
                title="Sign out"
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
              </button>
            </>
          ) : (
            <>
              <a href="#how-it-works" className="hidden rounded-lg px-3 py-2 text-zinc-300 hover:text-white md:block">How it works</a>
              <a href="#pricing" className="hidden rounded-lg px-3 py-2 text-zinc-300 hover:text-white md:block">Pricing</a>
              <Link to="/login" className="rounded-lg px-3 py-2 text-zinc-300 hover:text-white">Log in</Link>
              <Link to="/signup" className="rounded-full bg-white px-4 py-2 font-semibold text-ink-950 transition hover:shadow-lg hover:shadow-white/20">Get started</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
