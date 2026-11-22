import { Link } from 'react-router-dom'

export default function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <Link to="/" className="flex items-center gap-2 font-semibold tracking-tight" aria-label="HighVibe home">
      <span className="grid h-8 w-8 place-items-center rounded-lg bg-gradient-to-br from-brand to-brand-2 text-sm font-bold text-white shadow-lg shadow-brand/30">
        H
      </span>
      {!compact && <span className="text-lg">HighVibe</span>}
    </Link>
  )
}
