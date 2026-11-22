import { ArrowUp, Brain, CheckCircle2, FileCode2, FileSearch, Loader2, Sparkles, Square, XCircle } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { parseStreaming } from '../lib/parse'
import type { ChatEvent, ChatMessage } from '../lib/types'
import Markdown from './Markdown'

interface ChatPanelProps {
  messages: ChatMessage[]
  streamingText: string | null
  pendingPrompt: string | null
  error: string | null
  canEdit: boolean
  userInitials: string
  onSend: (text: string) => void
  onStop: () => void
}

const SUGGESTIONS = [
  'Add a dark mode toggle',
  'Make it look great on mobile',
  'Add a contact form with validation',
  'Use a bolder, more colourful design',
]

function EventView({ event, live }: { event: ChatEvent; live?: boolean }) {
  switch (event.type) {
    case 'THOUGHT':
      return (
        <div className="flex items-center gap-2 text-xs text-zinc-500">
          <Brain className="h-3.5 w-3.5" aria-hidden="true" /> {event.content}
        </div>
      )
    case 'TOOL_LOG':
      return (
        <div className="flex items-center gap-2 text-xs text-zinc-400">
          <FileSearch className="h-3.5 w-3.5" aria-hidden="true" />
          <span>Read {event.metadata?.split(',').map((p) => p.trim()).join(', ') || 'files'}</span>
        </div>
      )
    case 'FILE_EDIT': {
      const icon = live || event.status === 'PENDING'
        ? <Loader2 className="h-3.5 w-3.5 animate-spin text-violet-300" aria-label="Saving" />
        : event.status === 'FAILED'
          ? <XCircle className="h-3.5 w-3.5 text-rose-400" aria-label="Failed" />
          : <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" aria-label="Saved" />
      const lines = event.content ? event.content.split('\n').length : 0
      return (
        <div className={`flex items-center gap-2 rounded-xl border px-3 py-2 font-mono text-xs transition ${live ? 'border-violet-400/40 bg-violet-500/10' : 'border-white/10 bg-ink-800/70'}`}>
          <FileCode2 className="h-3.5 w-3.5 text-violet-300" aria-hidden="true" />
          <span className="truncate text-zinc-200">{event.filePath}</span>
          <span className="ml-auto flex shrink-0 items-center gap-1.5 text-zinc-500">{live ? 'writing' : `${lines} lines`} {icon}</span>
        </div>
      )
    }
    default:
      return event.content ? <div className="text-sm leading-relaxed text-zinc-200"><Markdown text={event.content} /></div> : null
  }
}

function AiAvatar() {
  return (
    <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-gradient-to-br from-brand via-fuchsia-500 to-brand-2 text-xs font-extrabold text-white shadow-md shadow-brand/30">
      H
    </span>
  )
}

function AssistantBubble({ events, active }: { events: ChatEvent[]; active?: ChatEvent | null }) {
  return (
    <div className="flex gap-3">
      <AiAvatar />
      <div className="min-w-0 flex-1 space-y-2.5 pt-0.5">
        {events.map((event, i) => <EventView key={event.id ?? `e${i}`} event={event} />)}
        {active && <EventView event={active} live />}
      </div>
    </div>
  )
}

function UserBubble({ text, initials }: { text: string; initials: string }) {
  return (
    <div className="flex justify-end gap-3">
      <div className="max-w-[85%] rounded-2xl rounded-tr-md bg-gradient-to-br from-violet-600/40 to-fuchsia-600/25 px-4 py-2.5 text-sm whitespace-pre-wrap ring-1 ring-white/10">{text}</div>
      <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-white/10 text-[11px] font-bold text-zinc-200">{initials}</span>
    </div>
  )
}

export default function ChatPanel({ messages, streamingText, pendingPrompt, error, canEdit, userInitials, onSend, onStop }: ChatPanelProps) {
  const [draft, setDraft] = useState('')
  const bottom = useRef<HTMLDivElement>(null)
  const busy = streamingText !== null
  const live = useMemo(() => (streamingText !== null ? parseStreaming(streamingText) : null), [streamingText])

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length, streamingText, error])

  const send = (text: string) => {
    const value = text.trim()
    if (!value || busy || !canEdit) return
    setDraft('')
    onSend(value)
  }

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    send(draft)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="flex h-full flex-col bg-ink-950">
      <div className="hv-scroll flex-1 space-y-6 overflow-y-auto px-4 py-5">
        {messages.length === 0 && !busy && (
          <div className="hv-card p-5">
            <div className="flex items-center gap-2 text-sm font-medium">
              <Sparkles className="h-4 w-4 text-violet-300" aria-hidden="true" /> What should we build?
            </div>
            <p className="mt-1.5 text-sm text-zinc-400">Describe a feature or a change. HighVibe edits the files and the preview updates on its own.</p>
            {canEdit && (
              <div className="mt-4 flex flex-wrap gap-2">
                {SUGGESTIONS.map((s) => (
                  <button key={s} onClick={() => send(s)} className="rounded-full border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-zinc-300 transition hover:border-violet-400/40 hover:text-white">
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
        {messages.map((m) =>
          m.role === 'USER'
            ? <UserBubble key={m.id} text={m.content ?? ''} initials={userInitials} />
            : <AssistantBubble key={m.id} events={m.events} />,
        )}
        {pendingPrompt && <UserBubble text={pendingPrompt} initials={userInitials} />}
        {busy && live && (
          <div>
            <AssistantBubble events={live.events} active={live.active} />
            {!live.active && (
              <div className="mt-2 ml-10 flex gap-1 text-violet-300" aria-label="Thinking">
                <span className="hv-dot">●</span><span className="hv-dot">●</span><span className="hv-dot">●</span>
              </div>
            )}
          </div>
        )}
        {error && <p role="alert" className="rounded-xl border border-rose-500/30 bg-rose-500/10 px-3.5 py-2.5 text-sm text-rose-200">{error}</p>}
        <div ref={bottom} />
      </div>

      <form onSubmit={submit} className="border-t border-white/10 bg-ink-900/40 p-3">
        <div className="rounded-2xl border border-white/10 bg-ink-800/80 p-2 transition focus-within:border-violet-400/50 focus-within:ring-4 focus-within:ring-violet-500/10">
          <label htmlFor="chat-input" className="sr-only">Message HighVibe</label>
          <textarea
            id="chat-input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={onKeyDown}
            disabled={!canEdit}
            rows={2}
            placeholder={canEdit ? 'Ask HighVibe to change something...' : 'You have view-only access to this project'}
            className="w-full resize-none bg-transparent px-2 py-1 text-sm outline-none placeholder:text-zinc-500 disabled:cursor-not-allowed"
          />
          <div className="flex items-center justify-between px-1">
            <span className="text-[11px] text-zinc-600">Enter to send · Shift + Enter for a new line</span>
            {busy ? (
              <button type="button" onClick={onStop} aria-label="Stop watching the stream"
                className="grid h-8 w-8 place-items-center rounded-full bg-white/10 hover:bg-white/20">
                <Square className="h-3.5 w-3.5" />
              </button>
            ) : (
              <button type="submit" disabled={!draft.trim() || !canEdit} aria-label="Send"
                className="grid h-8 w-8 place-items-center rounded-full bg-white text-ink-950 transition hover:scale-105 disabled:opacity-30 disabled:hover:scale-100">
                <ArrowUp className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      </form>
    </div>
  )
}
