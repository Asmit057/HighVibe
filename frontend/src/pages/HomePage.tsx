import { ArrowUp, Check, Clock, FolderOpen, Loader2, Trash2, X } from 'lucide-react'
import { useCallback, useEffect, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import Navbar from '../components/Navbar'
import { api } from '../lib/api'
import { useAuth } from '../lib/auth'
import type { Invitation, Project, ProjectSummary } from '../lib/types'

const PENDING_PROMPT_KEY = 'highvibe.pendingPrompt'

const SUGGESTIONS = [
  'A habit tracker with streaks and a weekly heatmap',
  'Landing page for a specialty coffee roaster',
  'Kanban board for a small design team',
  'Personal finance dashboard with charts',
]

function projectNameFrom(prompt: string) {
  const words = prompt.replace(/[^\p{L}\p{N} ]/gu, ' ').split(/\s+/).filter(Boolean).slice(0, 5)
  const name = words.join(' ')
  return name ? name.charAt(0).toUpperCase() + name.slice(1) : 'Untitled project'
}

function timeAgo(iso: string) {
  const seconds = Math.max(1, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  const units: [number, string][] = [[86400, 'd'], [3600, 'h'], [60, 'm']]
  for (const [size, label] of units) if (seconds >= size) return `${Math.floor(seconds / size)}${label} ago`
  return 'just now'
}

export default function HomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState(() => {
    try { return sessionStorage.getItem(PENDING_PROMPT_KEY) ?? '' } catch { return '' }
  })
  const [projects, setProjects] = useState<ProjectSummary[]>([])
  const [invitations, setInvitations] = useState<Invitation[]>([])
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!user) return
    try {
      const [p, i] = await Promise.all([
        api<ProjectSummary[]>('/api/projects'),
        api<Invitation[]>('/api/invitations'),
      ])
      setProjects(p)
      setInvitations(i)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load your projects')
    }
  }, [user])

  useEffect(() => { void load() }, [load])

  const create = async (e?: FormEvent) => {
    e?.preventDefault()
    const text = prompt.trim()
    if (!text) return
    if (!user) {
      try { sessionStorage.setItem(PENDING_PROMPT_KEY, text) } catch { /* ignore */ }
      navigate('/signup', { state: { from: '/' } })
      return
    }
    setCreating(true)
    setError(null)
    try {
      const project = await api<Project>('/api/projects', {
        method: 'POST',
        body: JSON.stringify({ name: projectNameFrom(text), description: text }),
      })
      try { sessionStorage.removeItem(PENDING_PROMPT_KEY) } catch { /* ignore */ }
      navigate(`/projects/${project.id}`, { state: { initialPrompt: text } })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the project')
      setCreating(false)
    }
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void create()
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
    <div className="hv-glow min-h-full">
      <Navbar />
      <main className="mx-auto max-w-6xl px-6 pb-24">
        <section className="mx-auto max-w-3xl pt-16 pb-14 text-center sm:pt-24">
          <h1 className="text-4xl font-semibold tracking-tight sm:text-6xl">
            Build something <span className="hv-gradient-text">with vibes</span>
          </h1>
          <p className="mt-4 text-lg text-zinc-400">Describe an app. HighVibe writes the code, runs it live, and keeps iterating with you.</p>

          <form onSubmit={create} className="mt-10 rounded-2xl border border-white/10 bg-ink-900/80 p-3 text-left shadow-2xl shadow-brand/10 backdrop-blur">
            <label htmlFor="prompt" className="sr-only">Describe the app you want to build</label>
            <textarea
              id="prompt"
              value={prompt}
              onChange={(e) => setPrompt(e.target.value)}
              onKeyDown={onKeyDown}
              rows={3}
              placeholder="Ask HighVibe to create a web app that..."
              className="w-full resize-none bg-transparent px-3 py-2 text-base outline-none placeholder:text-zinc-500"
            />
            <div className="flex items-center justify-between px-2 pb-1">
              <span className="text-xs text-zinc-500">React · TypeScript · Tailwind</span>
              <button type="submit" disabled={creating || !prompt.trim()} aria-label="Create project"
                className="grid h-9 w-9 place-items-center rounded-full bg-white text-ink-950 transition hover:scale-105 disabled:opacity-40">
                {creating ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
              </button>
            </div>
          </form>

          <div className="mt-5 flex flex-wrap justify-center gap-2">
            {SUGGESTIONS.map((s) => (
              <button key={s} type="button" onClick={() => setPrompt(s)}
                className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-sm text-zinc-300 hover:bg-white/10">
                {s}
              </button>
            ))}
          </div>
          {error && <p role="alert" className="mt-6 rounded-lg bg-rose-500/10 px-4 py-2 text-sm text-rose-300">{error}</p>}
        </section>

        {user && invitations.length > 0 && (
          <section className="mb-10 space-y-2">
            <h2 className="text-sm font-medium uppercase tracking-wider text-zinc-500">Invitations</h2>
            {invitations.map((inv) => (
              <div key={inv.projectId} className="flex items-center justify-between rounded-xl border border-violet-500/30 bg-violet-500/10 px-4 py-3">
                <span className="text-sm">You were invited to <strong>{inv.projectName}</strong> as {inv.role.toLowerCase()}</span>
                <div className="flex gap-2">
                  <button onClick={() => respond(inv, true)} className="flex items-center gap-1 rounded-lg bg-white px-3 py-1.5 text-sm font-medium text-ink-950">
                    <Check className="h-4 w-4" aria-hidden="true" /> Accept
                  </button>
                  <button onClick={() => respond(inv, false)} aria-label="Decline invitation" className="rounded-lg px-2 py-1.5 text-zinc-300 hover:bg-white/10">
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </section>
        )}

        {user && (
          <section>
            <h2 className="mb-4 text-sm font-medium uppercase tracking-wider text-zinc-500">Your projects</h2>
            {projects.length === 0 ? (
              <div className="rounded-2xl border border-dashed border-white/10 p-10 text-center text-zinc-500">
                <FolderOpen className="mx-auto mb-3 h-8 w-8" aria-hidden="true" />
                No projects yet. Describe your first app above.
              </div>
            ) : (
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {projects.map((p) => (
                  <article key={p.id} className="group relative rounded-2xl border border-white/10 bg-ink-900/70 p-5 transition hover:border-violet-400/40 hover:bg-ink-800">
                    <button onClick={() => navigate(`/projects/${p.id}`)} className="block w-full text-left">
                      <div className="mb-4 h-28 rounded-xl bg-gradient-to-br from-violet-500/30 via-fuchsia-500/20 to-rose-500/20" />
                      <h3 className="font-medium">{p.name}</h3>
                      <p className="mt-1 line-clamp-2 text-sm text-zinc-400">{p.description ?? 'No description'}</p>
                      <p className="mt-3 flex items-center gap-1.5 text-xs text-zinc-500">
                        <Clock className="h-3.5 w-3.5" aria-hidden="true" /> Edited {timeAgo(p.updatedAt)}
                        {p.role && p.role !== 'OWNER' && <span className="ml-auto rounded bg-white/10 px-1.5 py-0.5">{p.role.toLowerCase()}</span>}
                      </p>
                    </button>
                    {p.role === 'OWNER' && (
                      <button onClick={() => remove(p)} aria-label={`Delete ${p.name}`}
                        className="absolute top-3 right-3 rounded-lg p-1.5 text-zinc-400 opacity-0 transition group-hover:opacity-100 hover:bg-rose-500/20 hover:text-rose-300">
                        <Trash2 className="h-4 w-4" />
                      </button>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  )
}
