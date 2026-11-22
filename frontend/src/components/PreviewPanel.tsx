import { ExternalLink, Loader2, Monitor, Play, RotateCw, Smartphone, Square, Tablet } from 'lucide-react'
import { useState } from 'react'
import type { Preview } from '../lib/types'

interface PreviewPanelProps {
  preview: Preview | null
  reloadKey: number
  onStart: () => void
  onStop: () => void
  onReload: () => void
}

const DEVICES = [
  { id: 'desktop', label: 'Desktop', icon: Monitor, width: '100%' },
  { id: 'tablet', label: 'Tablet', icon: Tablet, width: '768px' },
  { id: 'mobile', label: 'Mobile', icon: Smartphone, width: '390px' },
] as const

const BOOT_STEPS = ['Copying project files', 'Installing dependencies', 'Starting the dev server']

export default function PreviewPanel({ preview, reloadKey, onStart, onStop, onReload }: PreviewPanelProps) {
  const [device, setDevice] = useState<(typeof DEVICES)[number]['id']>('desktop')
  const running = preview?.status === 'RUNNING' && preview.previewUrl
  const width = DEVICES.find((d) => d.id === device)!.width

  return (
    <div className="flex h-full flex-col bg-ink-950">
      {/* browser-style toolbar */}
      <div className="flex items-center gap-2 border-b border-white/10 bg-ink-900/60 px-3 py-2">
        <div className="flex gap-1.5 pr-1" aria-hidden="true">
          <span className="h-2.5 w-2.5 rounded-full bg-rose-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-amber-400/70" />
          <span className="h-2.5 w-2.5 rounded-full bg-emerald-400/70" />
        </div>
        <button onClick={onReload} disabled={!running} aria-label="Reload preview" title="Reload"
          className="rounded-md p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white disabled:opacity-30">
          <RotateCw className="h-3.5 w-3.5" />
        </button>
        <div className="flex min-w-0 flex-1 items-center gap-2 rounded-lg border border-white/10 bg-ink-950/80 px-3 py-1.5 font-mono text-xs">
          <span className={`h-2 w-2 shrink-0 rounded-full ${running ? 'bg-emerald-400' : preview?.status === 'CREATING' ? 'animate-pulse bg-amber-400' : 'bg-zinc-600'}`} aria-hidden="true" />
          <span className="truncate text-zinc-400">{running ? preview!.previewUrl : preview?.status === 'CREATING' ? 'starting...' : 'preview stopped'}</span>
        </div>
        <div className="hidden items-center rounded-lg bg-white/5 p-0.5 md:flex" role="group" aria-label="Preview size">
          {DEVICES.map(({ id, label, icon: Icon }) => (
            <button key={id} onClick={() => setDevice(id)} aria-label={label} title={label} aria-pressed={device === id}
              className={`rounded-md p-1.5 transition ${device === id ? 'bg-white/15 text-white' : 'text-zinc-500 hover:text-zinc-200'}`}>
              <Icon className="h-3.5 w-3.5" />
            </button>
          ))}
        </div>
        {running && (
          <>
            <a href={preview!.previewUrl!} target="_blank" rel="noreferrer" aria-label="Open preview in a new tab" title="Open in new tab"
              className="rounded-md p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><ExternalLink className="h-3.5 w-3.5" /></a>
            <button onClick={onStop} aria-label="Stop preview" title="Stop preview"
              className="rounded-md p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><Square className="h-3.5 w-3.5" /></button>
          </>
        )}
      </div>

      <div className="relative flex flex-1 justify-center overflow-auto bg-[radial-gradient(circle_at_1px_1px,rgba(255,255,255,0.06)_1px,transparent_0)] [background-size:18px_18px]">
        {running ? (
          <div className="h-full transition-all duration-300" style={{ width, maxWidth: '100%' }}>
            <iframe key={reloadKey} title="Live preview" src={preview!.previewUrl!}
              className={`h-full w-full border-0 bg-white ${device === 'desktop' ? '' : 'my-0 rounded-none shadow-2xl ring-1 ring-white/10'}`} />
          </div>
        ) : (
          <div className="grid w-full place-items-center p-8 text-center">
            {preview?.status === 'CREATING' ? (
              <div className="hv-card w-full max-w-sm p-7 text-left">
                <div className="flex items-center gap-3">
                  <Loader2 className="h-5 w-5 animate-spin text-violet-300" aria-hidden="true" />
                  <p className="font-semibold">Spinning up your preview</p>
                </div>
                <ul className="mt-5 space-y-3 text-sm text-zinc-400">
                  {BOOT_STEPS.map((step, i) => (
                    <li key={step} className="flex items-center gap-3">
                      <span className="hv-shimmer h-2 w-2 rounded-full" style={{ animationDelay: `${i * 200}ms` }} />
                      {step}
                    </li>
                  ))}
                </ul>
                <p className="mt-5 text-xs text-zinc-500">The first start installs packages and takes about a minute.</p>
              </div>
            ) : (
              <div className="max-w-md space-y-5">
                <div className="mx-auto grid h-14 w-14 place-items-center rounded-2xl bg-gradient-to-br from-brand/30 to-brand-2/20 ring-1 ring-white/10">
                  <Play className="h-6 w-6 text-violet-200" aria-hidden="true" />
                </div>
                {preview?.status === 'FAILED' && (
                  <pre className="hv-scroll max-h-48 overflow-auto rounded-xl border border-rose-500/30 bg-rose-500/10 p-3 text-left text-xs whitespace-pre-wrap text-rose-200">
                    {preview.failureReason}
                  </pre>
                )}
                <p className="text-zinc-400">{preview?.status === 'FAILED' ? 'The preview could not start.' : 'The live preview is not running.'}</p>
                <button onClick={onStart} className="inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 font-semibold text-ink-950 transition hover:shadow-lg hover:shadow-white/20">
                  <Play className="h-4 w-4" aria-hidden="true" /> Start preview
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
