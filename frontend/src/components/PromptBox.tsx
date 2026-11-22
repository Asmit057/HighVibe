import { ArrowUp, Loader2, Sparkles } from 'lucide-react'
import { useEffect, useState, type FormEvent, type KeyboardEvent } from 'react'

export const IDEAS = [
  'A habit tracker with streaks and a weekly heatmap',
  'A landing page for a specialty coffee roaster',
  'A kanban board for a small design team',
  'A personal finance dashboard with charts',
  'A recipe book with search and favourites',
  'A calculator with a history of results',
]

/** Cycles through example ideas with a typing animation while the box is empty. */
function useTypingPlaceholder(enabled: boolean) {
  const [text, setText] = useState('')
  useEffect(() => {
    if (!enabled) return
    let idea = 0
    let chars = 0
    let deleting = false
    const timer = setInterval(() => {
      const full = IDEAS[idea]
      if (!deleting) {
        chars++
        if (chars > full.length + 18) deleting = true // pause at the end
      } else {
        chars -= 2
        if (chars <= 0) {
          deleting = false
          chars = 0
          idea = (idea + 1) % IDEAS.length
        }
      }
      setText(full.slice(0, Math.min(chars, full.length)))
    }, 45)
    return () => clearInterval(timer)
  }, [enabled])
  return text
}

interface PromptBoxProps {
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  busy?: boolean
  autoFocus?: boolean
}

export default function PromptBox({ value, onChange, onSubmit, busy = false, autoFocus = false }: PromptBoxProps) {
  const typing = useTypingPlaceholder(value.length === 0)

  const submit = (e?: FormEvent) => {
    e?.preventDefault()
    if (value.trim() && !busy) onSubmit()
  }

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      submit()
    }
  }

  return (
    <div className="w-full">
      <form onSubmit={submit} className="hv-gradient-border p-[1px] text-left shadow-2xl shadow-brand/20">
        <div className="relative rounded-[1.2rem] bg-ink-900/95 p-3">
          <label htmlFor="prompt" className="sr-only">Describe the app you want to build</label>
          <textarea
            id="prompt"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            onKeyDown={onKeyDown}
            rows={3}
            autoFocus={autoFocus}
            className="relative z-10 w-full resize-none bg-transparent px-3 py-2 text-base outline-none sm:text-lg"
          />
          {value.length === 0 && (
            <div className="pointer-events-none absolute top-5 left-6 right-6 text-base text-zinc-500 sm:text-lg" aria-hidden="true">
              Ask HighVibe to build {typing.charAt(0).toLowerCase() + typing.slice(1)}
              <span className="hv-caret" />
            </div>
          )}
          <div className="flex items-center justify-between px-2 pb-1">
            <span className="flex items-center gap-1.5 text-xs text-zinc-500">
              <Sparkles className="h-3.5 w-3.5 text-violet-300" aria-hidden="true" /> React · TypeScript · Tailwind
            </span>
            <button type="submit" disabled={busy || !value.trim()} aria-label="Build it"
              className="flex items-center gap-2 rounded-full bg-white px-4 py-2 text-sm font-semibold text-ink-950 transition hover:scale-[1.03] hover:shadow-lg hover:shadow-white/20 disabled:opacity-40 disabled:hover:scale-100">
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
              Build
            </button>
          </div>
        </div>
      </form>

      <div className="mt-5 flex flex-wrap justify-center gap-2">
        {IDEAS.slice(0, 4).map((idea) => (
          <button key={idea} type="button" onClick={() => onChange(idea)}
            className="rounded-full border border-white/10 bg-white/[0.04] px-3.5 py-1.5 text-sm text-zinc-300 transition hover:border-violet-400/40 hover:bg-white/10 hover:text-white">
            {idea}
          </button>
        ))}
      </div>
    </div>
  )
}
