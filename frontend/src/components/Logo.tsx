import { Link } from 'react-router-dom'

export default function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <Link to="/" className="group flex items-center gap-2.5 font-semibold tracking-tight" aria-label="HighVibe home">
      <span className="relative grid h-8 w-8 place-items-center rounded-xl bg-gradient-to-br from-brand via-fuchsia-500 to-brand-2 text-sm font-extrabold text-white shadow-lg shadow-brand/30 transition group-hover:rotate-6">
        H
      </span>
      {!compact && <span className="text-lg">High<span className="hv-gradient-text">Vibe</span></span>}
    </Link>
  )
}
