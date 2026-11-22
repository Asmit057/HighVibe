import { Check, Clock, FolderOpen, Search, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import MiniAppMock from '../components/MiniAppMock'
import Navbar from '../components/Navbar'
import PromptBox from '../components/PromptBox'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import { createProjectFromPrompt, gradientFor, loadPendingPrompt, savePendingPrompt } from '../lib/create'
import type { Invitation, ProjectSummary } from '../lib/types'

function timeAgo(iso: string) {
  const seconds = Math.max(1, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  const units: [number, string][] = [[86400, 'd'], [3600, 'h'], [60, 'm']]
  for (const [size, label] of units) if (seconds >= size) return `${Math.floor(seconds / size)}${label} ago`
  return 'just now'
}

function greeting() {
  const hour = new Date().getHours()
  return hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening'
}

/** The signed-in home: prompt box, invitations and the project dashboard. */
export default function HomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState(loadPendingPrompt)
  const [projects, setProjects] = useState<ProjectSummary[] | null>(null)
  const [invitations, setInvitations] = useState<Invitation[]>([])
  const [query, setQuery] = useState('')
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const [p, i] = await Promise.all([api<ProjectSummary[]>('/api/projects'), api<Invitation[]>('/api/invitations')])
      setProjects(p)
      setInvitations(i)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load your projects')
      setProjects([])
    }
  }, [])

  useEffect(() => { void load() }, [load])

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase()
    return (projects ?? []).filter((p) => !q || p.name.toLowerCase().includes(q) || (p.description ?? '').toLowerCase().includes(q))
  }, [projects, query])

  const create = async () => {
    const text = prompt.trim()
    setCreating(true)
    setError(null)
    try {
      const project = await createProjectFromPrompt(text)
      savePendingPrompt(null)
      navigate(`/projects/${project.id}`, { state: { initialPrompt: text } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the project')
      setCreating(false)
    }
  }

  const respond = async (invitation: Invitation, accept: boolean) => {
    await api(accept ? `/api/invitations/${invitation.projectId}/accept` : `/api/invitations/${invitation.projectId}`,
      { method: accept ? 'POST' : 'DELETE' })
    await load()
  }

  const remove = async (project: ProjectSummary) => {
    if (!window.confirm(`Delete "${project.name}"? You can't undo this from the app.`)) return
    try {
      await api(`/api/projects/${project.id}`, { method: 'DELETE' })
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not delete the project')
    }
  }

  return (
    <div className="min-h-full">
      <Navbar />
      <section className="relative overflow-hidden">
        <div className="hv-aurora opacity-70" aria-hidden="true" />
        <div className="hv-grid" aria-hidden="true" />
        <div className="relative mx-auto max-w-3xl px-6 pt-16 pb-14 text-center">
          <p className="hv-fade-up text-sm text-zinc-400">{greeting()}, {user?.name?.split(' ')[0]}</p>
          <h1 className="hv-fade-up mt-2 text-4xl font-extrabold tracking-tight sm:text-5xl" style={{ animationDelay: '80ms' }}>
            What do you want to <span className="hv-gradient-text">build today?</span>
          </h1>
          <div className="hv-fade-up mt-9" style={{ animationDelay: '160ms' }}>
            <PromptBox value={prompt} onChange={setPrompt} onSubmit={create} busy={creating} autoFocus />
          </div>
          {error && <p role="alert" className="mt-6 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-2.5 text-sm text-rose-200">{error}</p>}
        </div>
      </section>

      <main className="mx-auto max-w-6xl px-6 pb-24">
        {invitations.length > 0 && (
          <section className="mb-10 space-y-2">
            <h2 className="text-xs font-semibold tracking-widest text-zinc-500 uppercase">Invitations</h2>
            {invitations.map((inv) => (
              <div key={inv.projectId} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-violet-500/30 bg-gradient-to-r from-violet-500/15 to-transparent px-5 py-3.5">
                <span className="text-sm">You were invited to <strong>{inv.projectName}</strong> as {inv.role.toLowerCase()}</span>
                <div className="flex gap-2">
                  <button onClick={() => respond(inv, true)} className="flex items-center gap-1.5 rounded-full bg-white px-3.5 py-1.5 text-sm font-semibold text-ink-950">
                    <Check className="h-4 w-4" aria-hidden="true" /> Accept
                  </button>
                  <button onClick={() => respond(inv, false)} aria-label="Decline invitation" className="rounded-full p-2 text-zinc-300 hover:bg-white/10">
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </section>
        )}

        <section>
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <h2 className="flex items-center gap-2 text-xl font-semibold">
              Your projects
              {projects && <span className="rounded-full bg-white/10 px-2 py-0.5 text-xs font-medium text-zinc-300">{projects.length}</span>}
            </h2>
            {projects && projects.length > 0 && (
              <label className="flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3.5 py-2 text-sm focus-within:border-violet-400/50">
                <Search className="h-4 w-4 text-zinc-500" aria-hidden="true" />
                <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search projects" aria-label="Search projects"
                  className="w-48 bg-transparent outline-none placeholder:text-zinc-500" />
              </label>
            )}
          </div>

          {projects === null ? (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {[0, 1, 2].map((i) => <div key={i} className="hv-shimmer h-64 rounded-2xl" />)}
            </div>
          ) : projects.length === 0 ? (
            <div className="hv-card flex flex-col items-center p-12 text-center">
              <FolderOpen className="h-10 w-10 text-violet-300" aria-hidden="true" />
              <p className="mt-4 font-medium">No projects yet</p>
              <p className="mt-1 text-sm text-zinc-400">Describe your first app above and HighVibe will build it.</p>
            </div>
          ) : visible.length === 0 ? (
            <p className="text-sm text-zinc-500">No project matches "{query}".</p>
          ) : (
            <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {visible.map((p) => (
                <article key={p.id} className="group hv-card relative overflow-hidden transition hover:-translate-y-1 hover:border-violet-400/40 hover:shadow-2xl hover:shadow-brand/10">
                  <Link to={`/projects/${p.id}`} className="block">
                    <div className="h-36"><MiniAppMock gradient={gradientFor(p.id)} variant={p.id} /></div>
                    <div className="p-5">
                      <h3 className="truncate font-semibold">{p.name}</h3>
                      <p className="mt-1 line-clamp-2 min-h-10 text-sm text-zinc-400">{p.description ?? 'No description'}</p>
                      <p className="mt-4 flex items-center gap-1.5 text-xs text-zinc-500">
                        <Clock className="h-3.5 w-3.5" aria-hidden="true" /> Edited {timeAgo(p.updatedAt)}
                        {p.role && p.role !== 'OWNER' && <span className="ml-auto rounded-full bg-white/10 px-2 py-0.5 text-zinc-300">{p.role.toLowerCase()}</span>}
                      </p>
                    </div>
                  </Link>
                  {p.role === 'OWNER' && (
                    <button onClick={() => remove(p)} aria-label={`Delete ${p.name}`}
                      className="absolute top-3 right-3 rounded-full bg-ink-950/70 p-2 text-zinc-300 opacity-0 backdrop-blur transition group-hover:opacity-100 focus-visible:opacity-100 pointer-coarse:opacity-100 hover:bg-rose-500/80 hover:text-white">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
