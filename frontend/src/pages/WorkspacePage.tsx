import { ArrowLeft, Code2, Download, Eye, MessageSquare, Users } from 'lucide-react'
import { useCallback, useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import ChatPanel from '../components/ChatPanel'
import CodePanel from '../components/CodePanel'
import Logo from '../components/Logo'
import MembersDialog from '../components/MembersDialog'
import PreviewPanel from '../components/PreviewPanel'
import { api, ApiError, authFetch } from '../lib/api'
import { useAuth } from '../lib/auth'
import { streamChat } from '../lib/stream'
import type { ChatMessage, FileNode, Preview, Project } from '../lib/types'

type Tab = 'chat' | 'preview' | 'code'

const TABS = {
  chat: { label: 'Chat', icon: MessageSquare },
  preview: { label: 'Preview', icon: Eye },
  code: { label: 'Code', icon: Code2 },
} as const

const CHAT_WIDTH_KEY = 'highvibe.chatWidth'
const NARROW_QUERY = '(max-width: 767px)'

/** True on phone-sized screens, where chat, preview and code share one pane. */
function useNarrow() {
  const [narrow, setNarrow] = useState(() => window.matchMedia(NARROW_QUERY).matches)
  useEffect(() => {
    const mq = window.matchMedia(NARROW_QUERY)
    const update = () => setNarrow(mq.matches)
    mq.addEventListener('change', update)
    return () => mq.removeEventListener('change', update)
  }, [])
  return narrow
}

/** Width of the chat column, draggable and remembered in this browser. */
function useChatWidth() {
  const [width, setWidth] = useState(() => {
    try { return Number(localStorage.getItem(CHAT_WIDTH_KEY)) || 420 } catch { return 420 }
  })
  const startDrag = (e: ReactPointerEvent<HTMLDivElement>) => {
    e.preventDefault()
    const move = (ev: PointerEvent) => setWidth(Math.min(Math.max(ev.clientX, 320), window.innerWidth * 0.6))
    const up = () => {
      window.removeEventListener('pointermove', move)
      window.removeEventListener('pointerup', up)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }
    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
  }
  useEffect(() => {
    try { localStorage.setItem(CHAT_WIDTH_KEY, String(Math.round(width))) } catch { /* ignore */ }
  }, [width])
  return { width, startDrag }
}

export default function WorkspacePage() {
  const { projectId: idParam } = useParams()
  const projectId = Number(idParam)
  const { user } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const [project, setProject] = useState<Project | null>(null)
  const [files, setFiles] = useState<FileNode[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [preview, setPreview] = useState<Preview | null>(null)
  const narrow = useNarrow()
  const [tab, setTab] = useState<Tab>(() => (window.matchMedia(NARROW_QUERY).matches ? 'chat' : 'preview'))
  const [streamingText, setStreamingText] = useState<string | null>(null)
  const [pendingPrompt, setPendingPrompt] = useState<string | null>(null)
  const [chatError, setChatError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showMembers, setShowMembers] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [codeRefreshKey, setCodeRefreshKey] = useState(0)

  const { width: chatWidth, startDrag } = useChatWidth()
  const userInitials = user?.name?.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() ?? 'ME'
  const abortRef = useRef<AbortController | null>(null)
  const initialPromptSent = useRef(false)
  const canEdit = project?.role === 'OWNER' || project?.role === 'EDITOR'
  const tabs: Tab[] = narrow ? ['chat', 'preview', 'code'] : ['preview', 'code']
  const active: Tab = !narrow && tab === 'chat' ? 'preview' : tab

  const loadFiles = useCallback(async () => {
    const tree = await api<{ files: FileNode[] }>(`/api/projects/${projectId}/files`)
    setFiles(tree.files)
  }, [projectId])

  const loadHistory = useCallback(async () => {
    setMessages(await api<ChatMessage[]>(`/api/chat/projects/${projectId}`))
  }, [projectId])

  const startPreview = useCallback(async () => {
    try {
      setPreview(await api<Preview>(`/api/projects/${projectId}/deploy`, { method: 'POST' }))
    } catch (e) {
      setPreview({ id: 0, projectId, status: 'FAILED', previewUrl: null, failureReason: e instanceof Error ? e.message : 'Could not start the preview', startedAt: null, expiresAt: null })
    }
  }, [projectId])

  // initial load
  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const p = await api<Project>(`/api/projects/${projectId}`)
        if (cancelled) return
        setProject(p)
        await Promise.all([loadFiles(), loadHistory()])
        let current: Preview | null = null
        try {
          current = await api<Preview>(`/api/projects/${projectId}/preview`)
        } catch (e) {
          if (!(e instanceof ApiError && e.status === 404)) throw e
        }
        if (cancelled) return
        setPreview(current)
        if (!current || current.status === 'TERMINATED') void startPreview()
      } catch (e) {
        if (!cancelled) setLoadError(e instanceof Error ? e.message : 'Could not open the project')
      }
    })()
    return () => { cancelled = true }
  }, [projectId, loadFiles, loadHistory, startPreview])

  // poll while the preview boots
  useEffect(() => {
    if (preview?.status !== 'CREATING') return
    const timer = setInterval(async () => {
      try {
        setPreview(await api<Preview>(`/api/projects/${projectId}/preview`))
      } catch { /* keep polling */ }
    }, 2000)
    return () => clearInterval(timer)
  }, [preview?.status, projectId])

  const send = useCallback(async (text: string) => {
    setChatError(null)
    setPendingPrompt(text)
    setStreamingText('')
    const controller = new AbortController()
    abortRef.current = controller

    await streamChat(projectId, text, {
      onDelta: (delta) => setStreamingText((prev) => (prev ?? '') + delta),
      onRestart: () => setStreamingText(''),
      onDone: async () => {
        setStreamingText(null)
        setPendingPrompt(null)
        await loadHistory()
        // file saves are confirmed asynchronously through the Kafka saga
        for (const delay of [1500, 4000]) {
          setTimeout(() => {
            void loadHistory()
            void loadFiles()
            setCodeRefreshKey((k) => k + 1)
          }, delay)
        }
      },
      onError: (message) => {
        setStreamingText(null)
        setPendingPrompt(null)
        setChatError(message)
        void loadHistory()
      },
    }, controller.signal)
  }, [projectId, loadFiles, loadHistory])

  // the first prompt typed on the home page
  useEffect(() => {
    const initial = (location.state as { initialPrompt?: string } | null)?.initialPrompt
    if (!initial || !project || !canEdit || initialPromptSent.current) return
    initialPromptSent.current = true
    navigate(location.pathname, { replace: true, state: null })
    void send(initial)
  }, [project, canEdit, location, navigate, send])

  const stopStream = () => {
    abortRef.current?.abort()
    setStreamingText(null)
    setPendingPrompt(null)
    // the generation keeps running on the server; its result appears once finished
    setTimeout(() => void loadHistory(), 3000)
  }

  const stopPreview = async () => {
    await api(`/api/projects/${projectId}/preview`, { method: 'DELETE' })
    setPreview((p) => (p ? { ...p, status: 'TERMINATED', previewUrl: null } : p))
  }

  const download = async () => {
    const res = await authFetch(`/api/projects/${projectId}/download`)
    const url = URL.createObjectURL(await res.blob())
    const a = document.createElement('a')
    a.href = url
    a.download = `${project?.name ?? 'project'}.zip`
    a.click()
    URL.revokeObjectURL(url)
  }

  if (loadError) {
    return (
      <div className="grid h-full place-items-center p-6 text-center">
        <div className="space-y-4">
          <p className="text-rose-300">{loadError}</p>
          <Link to="/" className="text-violet-300">Back to your projects</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col bg-ink-950">
      <header className="flex items-center gap-2 border-b border-white/10 bg-ink-900/60 px-2 py-2.5 backdrop-blur sm:gap-3 sm:px-4">
        <Link to="/" aria-label="Back to projects" className="shrink-0 rounded p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><ArrowLeft className="h-4 w-4" /></Link>
        <span className="hidden sm:block"><Logo compact /></span>
        <div className="min-w-0 flex-1 md:flex-none">
          <h1 className="truncate text-sm font-semibold">{project?.name ?? 'Loading...'}</h1>
          <p className="hidden items-center gap-1.5 text-[11px] text-zinc-500 sm:flex">
            <span className={`h-1.5 w-1.5 rounded-full ${streamingText !== null ? 'animate-pulse bg-violet-400' : preview?.status === 'RUNNING' ? 'bg-emerald-400' : 'bg-zinc-600'}`} aria-hidden="true" />
            {streamingText !== null ? 'AI is writing code...' : preview?.status === 'RUNNING' ? 'Preview live' : 'Preview offline'}
          </p>
        </div>
        {project?.role && project.role !== 'OWNER' && <span className="hidden rounded bg-white/10 px-1.5 py-0.5 text-xs text-zinc-400 sm:inline">{project.role.toLowerCase()}</span>}

        <div className="flex shrink-0 rounded-xl border border-white/10 bg-white/5 p-1 text-sm md:mx-auto" role="tablist">
          {tabs.map((t) => {
            const { label, icon: Icon } = TABS[t]
            return (
              <button key={t} role="tab" aria-selected={active === t} aria-label={label} onClick={() => setTab(t)}
                className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1 transition sm:px-3.5 ${active === t ? 'bg-white text-ink-950 shadow' : 'text-zinc-400 hover:text-white'}`}>
                <Icon className="h-4 w-4" aria-hidden="true" />
                <span className="hidden sm:inline">{label}</span>
              </button>
            )
          })}
        </div>

        <button onClick={download} aria-label="Download code" className="flex shrink-0 items-center gap-1.5 rounded-lg p-1.5 text-sm text-zinc-300 hover:bg-white/10 md:px-3">
          <Download className="h-4 w-4" aria-hidden="true" /> <span className="hidden md:inline">Download</span>
        </button>
        <button onClick={() => setShowMembers(true)} aria-label="Share project" className="flex shrink-0 items-center gap-1.5 rounded-full bg-gradient-to-r from-brand to-brand-2 p-2 text-sm font-semibold text-white shadow-lg shadow-brand/20 transition hover:brightness-110 sm:px-4 sm:py-1.5">
          <Users className="h-4 w-4" aria-hidden="true" /> <span className="hidden sm:inline">Share</span>
        </button>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className={narrow && active !== 'chat' ? 'hidden' : 'min-w-0 shrink-0'} style={{ width: narrow ? '100%' : chatWidth }}>
          <ChatPanel
            messages={messages}
            streamingText={streamingText}
            pendingPrompt={pendingPrompt}
            error={chatError}
            canEdit={canEdit}
            userInitials={userInitials}
            onSend={send}
            onStop={stopStream}
          />
        </aside>
        {!narrow && (
          <div role="separator" aria-orientation="vertical" aria-label="Resize chat" onPointerDown={startDrag}
            className="group relative w-px shrink-0 cursor-col-resize bg-white/10">
            <span className="absolute inset-y-0 -right-1.5 -left-1.5 transition group-hover:bg-violet-500/30" />
          </div>
        )}
        <section className={narrow && active === 'chat' ? 'hidden' : 'min-w-0 flex-1'}>
          {active === 'preview' ? (
            <PreviewPanel preview={preview} reloadKey={reloadKey} onStart={startPreview} onStop={stopPreview} onReload={() => setReloadKey((k) => k + 1)} />
          ) : (
            <CodePanel projectId={projectId} files={files} canEdit={canEdit} refreshKey={codeRefreshKey} />
          )}
        </section>
      </div>

      {showMembers && user && (
        <MembersDialog projectId={projectId} myRole={project?.role ?? null} myUserId={user.id} onClose={() => setShowMembers(false)} />
      )}
    </div>
  )
}
