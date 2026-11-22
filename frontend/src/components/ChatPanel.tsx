import { ArrowUp, Brain, CheckCircle2, FileCode2, FileSearch, Loader2, Square, XCircle } from 'lucide-react'
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
  onSend: (text: string) => void
  onStop: () => void
}

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
        <div className="flex items-center gap-2 rounded-lg border border-white/10 bg-ink-800/80 px-3 py-2 font-mono text-xs">
          <FileCode2 className="h-3.5 w-3.5 text-zinc-400" aria-hidden="true" />
          <span className="truncate">{event.filePath}</span>
          <span className="ml-auto flex items-center gap-1.5 text-zinc-500">{live ? 'writing' : `${lines} lines`} {icon}</span>
        </div>
      )
    }
    default:
      return event.content ? <div className="text-sm leading-relaxed text-zinc-200"><Markdown text={event.content} /></div> : null
  }
}

function AssistantBubble({ events, active }: { events: ChatEvent[]; active?: ChatEvent | null }) {
  return (
    <div className="space-y-2.5">
      {events.map((event, i) => <EventView key={event.id ?? `e${i}`} event={event} />)}
      {active && <EventView event={active} live />}
    </div>
  )
}

export default function ChatPanel({ messages, streamingText, pendingPrompt, error, canEdit, onSend, onStop }: ChatPanelProps) {
  const [draft, setDraft] = useState('')
  const bottom = useRef<HTMLDivElement>(null)
  const busy = streamingText !== null
  const live = useMemo(() => (streamingText !== null ? parseStreaming(streamingText) : null), [streamingText])

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages.length, streamingText, error])

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    const text = draft.trim()
    if (!text || busy) return
    setDraft('')
    onSend(text)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="flex h-full flex-col">
      <div className="hv-scroll flex-1 space-y-5 overflow-y-auto px-4 py-5">
        {messages.length === 0 && !busy && (
          <div className="rounded-xl border border-dashed border-white/10 p-5 text-sm text-zinc-500">
            Tell HighVibe what to build or change. It edits the files and the preview updates on its own.
          </div>
        )}
        {messages.map((m) =>
          m.role === 'USER' ? (
            <div key={m.id} className="ml-8 rounded-2xl rounded-tr-sm bg-white/10 px-4 py-2.5 text-sm whitespace-pre-wrap">{m.content}</div>
          ) : (
            <AssistantBubble key={m.id} events={m.events} />
          ),
        )}
        {pendingPrompt && (
          <div className="ml-8 rounded-2xl rounded-tr-sm bg-white/10 px-4 py-2.5 text-sm whitespace-pre-wrap">{pendingPrompt}</div>
        )}
        {busy && live && (
          <div>
            <AssistantBubble events={live.events} active={live.active} />
            {!live.active && (
              <div className="mt-2 flex gap-1 text-zinc-400" aria-label="Thinking">
                <span className="hv-dot">●</span><span className="hv-dot">●</span><span className="hv-dot">●</span>
              </div>
            )}
          </div>
        )}
        {error && <p role="alert" className="rounded-lg bg-rose-500/10 px-3 py-2 text-sm text-rose-300">{error}</p>}
        <div ref={bottom} />
      </div>

      <form onSubmit={submit} className="border-t border-white/10 p-3">
        <div className="rounded-xl border border-white/10 bg-ink-800 p-2 focus-within:border-violet-400/50">
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
          <div className="flex justify-end">
            {busy ? (
              <button type="button" onClick={onStop} aria-label="Stop watching the stream"
                className="grid h-8 w-8 place-items-center rounded-full bg-white/10 hover:bg-white/20">
                <Square className="h-3.5 w-3.5" />
              </button>
            ) : (
              <button type="submit" disabled={!draft.trim() || !canEdit} aria-label="Send"
                className="grid h-8 w-8 place-items-center rounded-full bg-white text-ink-950 disabled:opacity-30">
                <ArrowUp className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      </form>
    </div>
  )
}
