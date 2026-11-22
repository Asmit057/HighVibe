import { ArrowLeft, Code2, Download, Eye, Users } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
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

type Tab = 'preview' | 'code'

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
  const [tab, setTab] = useState<Tab>('preview')
  const [streamingText, setStreamingText] = useState<string | null>(null)
  const [pendingPrompt, setPendingPrompt] = useState<string | null>(null)
  const [chatError, setChatError] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [showMembers, setShowMembers] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)
  const [codeRefreshKey, setCodeRefreshKey] = useState(0)

  const abortRef = useRef<AbortController | null>(null)
  const initialPromptSent = useRef(false)
  const canEdit = project?.role === 'OWNER' || project?.role === 'EDITOR'

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
      <header className="flex items-center gap-3 border-b border-white/10 px-4 py-2.5">
        <Link to="/" aria-label="Back to projects" className="rounded p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><ArrowLeft className="h-4 w-4" /></Link>
        <Logo compact />
        <h1 className="truncate font-medium">{project?.name ?? 'Loading...'}</h1>
        {project?.role && project.role !== 'OWNER' && <span className="rounded bg-white/10 px-1.5 py-0.5 text-xs text-zinc-400">{project.role.toLowerCase()}</span>}

        <div className="mx-auto flex rounded-lg bg-white/5 p-0.5 text-sm" role="tablist">
          {(['preview', 'code'] as const).map((t) => (
            <button key={t} role="tab" aria-selected={tab === t} onClick={() => setTab(t)}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1 ${tab === t ? 'bg-white/10 text-white' : 'text-zinc-400 hover:text-white'}`}>
              {t === 'preview' ? <Eye className="h-4 w-4" aria-hidden="true" /> : <Code2 className="h-4 w-4" aria-hidden="true" />}
              {t === 'preview' ? 'Preview' : 'Code'}
            </button>
          ))}
        </div>

        <button onClick={download} className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-zinc-300 hover:bg-white/10">
          <Download className="h-4 w-4" aria-hidden="true" /> <span className="hidden md:inline">Download</span>
        </button>
        <button onClick={() => setShowMembers(true)} className="flex items-center gap-1.5 rounded-lg bg-white px-3 py-1.5 text-sm font-medium text-ink-950">
          <Users className="h-4 w-4" aria-hidden="true" /> Share
        </button>
      </header>

      <div className="flex min-h-0 flex-1">
        <aside className="w-full max-w-[420px] min-w-[320px] border-r border-white/10">
          <ChatPanel
            messages={messages}
            streamingText={streamingText}
            pendingPrompt={pendingPrompt}
            error={chatError}
            canEdit={canEdit}
            onSend={send}
            onStop={stopStream}
          />
        </aside>
        <section className="min-w-0 flex-1">
          {tab === 'preview' ? (
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
