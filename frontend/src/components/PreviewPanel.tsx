import { ExternalLink, Loader2, Play, RotateCw, Square } from 'lucide-react'
import type { Preview } from '../lib/types'

interface PreviewPanelProps {
  preview: Preview | null
  reloadKey: number
  onStart: () => void
  onStop: () => void
  onReload: () => void
}

export default function PreviewPanel({ preview, reloadKey, onStart, onStop, onReload }: PreviewPanelProps) {
  const running = preview?.status === 'RUNNING' && preview.previewUrl

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b border-white/10 px-3 py-2 text-xs">
        <span className={`h-2 w-2 rounded-full ${running ? 'bg-emerald-400' : preview?.status === 'CREATING' ? 'animate-pulse bg-amber-400' : 'bg-zinc-600'}`} aria-hidden="true" />
        <span className="truncate font-mono text-zinc-400">{running ? preview!.previewUrl : preview?.status?.toLowerCase() ?? 'not started'}</span>
        <div className="ml-auto flex items-center gap-1">
          {running && (
            <>
              <button onClick={onReload} aria-label="Reload preview" className="rounded p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><RotateCw className="h-3.5 w-3.5" /></button>
              <a href={preview!.previewUrl!} target="_blank" rel="noreferrer" aria-label="Open preview in a new tab" className="rounded p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><ExternalLink className="h-3.5 w-3.5" /></a>
              <button onClick={onStop} aria-label="Stop preview" className="rounded p-1.5 text-zinc-400 hover:bg-white/10 hover:text-white"><Square className="h-3.5 w-3.5" /></button>
            </>
          )}
        </div>
      </div>

      <div className="relative flex-1 bg-white">
        {running ? (
          <iframe key={reloadKey} title="Live preview" src={preview!.previewUrl!} className="h-full w-full border-0" />
        ) : (
          <div className="absolute inset-0 grid place-items-center bg-ink-950 p-8 text-center">
            {preview?.status === 'CREATING' ? (
              <div className="space-y-3 text-zinc-400">
                <Loader2 className="mx-auto h-8 w-8 animate-spin text-violet-300" aria-hidden="true" />
                <p className="font-medium text-zinc-200">Spinning up your preview</p>
                <p className="text-sm">Installing dependencies and starting the dev server. The first start takes a minute.</p>
              </div>
            ) : (
              <div className="max-w-md space-y-4 text-zinc-400">
                {preview?.status === 'FAILED' && (
                  <pre className="hv-scroll max-h-48 overflow-auto rounded-lg bg-rose-500/10 p-3 text-left text-xs whitespace-pre-wrap text-rose-300">
                    {preview.failureReason}
                  </pre>
                )}
                <p>{preview?.status === 'FAILED' ? 'The preview could not start.' : 'The live preview is not running.'}</p>
                <button onClick={onStart} className="inline-flex items-center gap-2 rounded-lg bg-white px-4 py-2 font-medium text-ink-950">
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
