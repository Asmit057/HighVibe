import { CreditCard, LogOut } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../lib/auth'
import Logo from './Logo'

export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <header className="relative z-10 mx-auto flex max-w-6xl items-center justify-between px-6 py-5">
      <Logo />
      <nav className="flex items-center gap-2 text-sm">
        {user ? (
          <>
            <Link to="/billing" className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-zinc-300 hover:bg-white/5 hover:text-white">
              <CreditCard className="h-4 w-4" aria-hidden="true" /> Plans
            </Link>
            <span className="hidden rounded-lg bg-white/5 px-3 py-2 text-zinc-300 sm:inline">{user.name}</span>
            <button
              type="button"
              onClick={async () => {
                await logout()
                navigate('/')
              }}
              className="flex items-center gap-1.5 rounded-lg px-3 py-2 text-zinc-300 hover:bg-white/5 hover:text-white"
              aria-label="Sign out"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
            </button>
          </>
        ) : (
          <>
            <Link to="/login" className="rounded-lg px-3 py-2 text-zinc-300 hover:text-white">Log in</Link>
            <Link to="/signup" className="rounded-lg bg-white px-4 py-2 font-medium text-ink-950 hover:bg-zinc-200">Get started</Link>
          </>
        )}
      </nav>
    </header>
  )
}
