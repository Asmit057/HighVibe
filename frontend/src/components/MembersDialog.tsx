import { Loader2, Trash2, UserPlus, X } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import type { Member, ProjectRole } from '../lib/types'

interface MembersDialogProps {
  projectId: number
  myRole: ProjectRole | null
  myUserId: number
  onClose: () => void
}

export default function MembersDialog({ projectId, myRole, myUserId, onClose }: MembersDialogProps) {
  const [members, setMembers] = useState<Member[]>([])
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<ProjectRole>('EDITOR')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isOwner = myRole === 'OWNER'

  const load = () => api<Member[]>(`/api/projects/${projectId}/members`).then(setMembers).catch((e) => setError(e.message))
  useEffect(() => { void load() }, [projectId]) // eslint-disable-line react-hooks/exhaustive-deps

  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Request failed')
    } finally {
      setBusy(false)
    }
  }

  const invite = (e: FormEvent) => {
    e.preventDefault()
    void run(async () => {
      await api(`/api/projects/${projectId}/members`, { method: 'POST', body: JSON.stringify({ username: email, role }) })
      setEmail('')
    })
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/60 p-4" role="dialog" aria-modal="true" aria-label="Project members">
      <div className="w-full max-w-lg rounded-2xl border border-white/10 bg-ink-900 p-6 shadow-2xl">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-semibold">Share project</h2>
          <button onClick={onClose} aria-label="Close" className="rounded p-1 text-zinc-400 hover:bg-white/10"><X className="h-4 w-4" /></button>
        </div>

        {isOwner && (
          <form onSubmit={invite} className="mb-5 flex gap-2">
            <input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="teammate@example.com"
              aria-label="Email of the person to invite"
              className="min-w-0 flex-1 rounded-lg border border-white/10 bg-ink-800 px-3 py-2 text-sm outline-none focus:border-violet-400/50" />
            <select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)} aria-label="Role"
              className="rounded-lg border border-white/10 bg-ink-800 px-2 text-sm">
              <option value="EDITOR">Editor</option>
              <option value="VIEWER">Viewer</option>
            </select>
            <button disabled={busy} className="flex items-center gap-1.5 rounded-lg bg-white px-3 text-sm font-medium text-ink-950 disabled:opacity-50">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <UserPlus className="h-4 w-4" />} Invite
            </button>
          </form>
        )}
        {error && <p role="alert" className="mb-3 rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>}

        <ul className="space-y-2">
          {members.map((m) => (
            <li key={m.userId} className="flex items-center gap-3 rounded-lg bg-white/5 px-3 py-2 text-sm">
              <div className="min-w-0 flex-1">
                <p className="truncate">{m.name ?? 'User ' + m.userId}{m.userId === myUserId && ' (you)'}</p>
                <p className="truncate text-xs text-zinc-500">{m.username}{!m.acceptedAt && ' · invitation pending'}</p>
              </div>
              {isOwner && m.projectRole !== 'OWNER' ? (
                <select value={m.projectRole} aria-label={`Role of ${m.username}`}
                  onChange={(e) => run(() => api(`/api/projects/${projectId}/members/${m.userId}`, { method: 'PATCH', body: JSON.stringify({ role: e.target.value }) }))}
                  className="rounded border border-white/10 bg-ink-800 px-1.5 py-1 text-xs">
                  <option value="EDITOR">Editor</option>
                  <option value="VIEWER">Viewer</option>
                </select>
              ) : (
                <span className="text-xs text-zinc-400">{m.projectRole.toLowerCase()}</span>
              )}
              {m.projectRole !== 'OWNER' && (isOwner || m.userId === myUserId) && (
                <button aria-label={m.userId === myUserId ? 'Leave project' : `Remove ${m.username}`}
                  onClick={() => run(() => api(`/api/projects/${projectId}/members/${m.userId}`, { method: 'DELETE' }))}
                  className="rounded p-1 text-zinc-400 hover:bg-rose-500/20 hover:text-rose-300">
                  <Trash2 className="h-3.5 w-3.5" />
                </button>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
