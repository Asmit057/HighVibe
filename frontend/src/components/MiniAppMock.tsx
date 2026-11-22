/** A tiny, abstract "app screenshot" drawn with divs, used on cards and the landing page. */
export default function MiniAppMock({ gradient, variant = 0 }: { gradient: string; variant?: number }) {
  return (
    <div className={`relative h-full w-full overflow-hidden bg-gradient-to-br ${gradient}`}>
      <div className="absolute inset-x-3 top-3 bottom-0 rounded-t-lg border border-white/15 bg-ink-950/70 p-2.5 shadow-xl backdrop-blur">
        <div className="mb-2.5 flex items-center gap-1">
          <span className="h-1.5 w-1.5 rounded-full bg-rose-400/80" />
          <span className="h-1.5 w-1.5 rounded-full bg-amber-400/80" />
          <span className="h-1.5 w-1.5 rounded-full bg-emerald-400/80" />
          <span className="ml-2 h-1.5 w-16 rounded-full bg-white/10" />
        </div>
        {variant % 3 === 0 && (
          <div className="space-y-1.5">
            <div className="h-2.5 w-2/3 rounded bg-white/70" />
            <div className="h-1.5 w-1/2 rounded bg-white/25" />
            <div className="mt-2.5 grid grid-cols-3 gap-1.5">
              {[0, 1, 2].map((i) => <div key={i} className="h-9 rounded-md bg-white/10" />)}
            </div>
          </div>
        )}
        {variant % 3 === 1 && (
          <div className="flex gap-1.5">
            {[0, 1, 2].map((col) => (
              <div key={col} className="flex-1 space-y-1.5 rounded-md bg-white/5 p-1.5">
                <div className="h-1.5 w-2/3 rounded bg-white/40" />
                {Array.from({ length: 3 - col % 2 }).map((_, i) => <div key={i} className="h-5 rounded bg-white/15" />)}
              </div>
            ))}
          </div>
        )}
        {variant % 3 === 2 && (
          <div className="space-y-1.5">
            <div className="flex items-end gap-1 h-12">
              {[40, 70, 55, 85, 60, 95, 75].map((h, i) => (
                <div key={i} className="flex-1 rounded-t bg-white/40" style={{ height: `${h}%` }} />
              ))}
            </div>
            <div className="h-1.5 w-3/4 rounded bg-white/25" />
          </div>
        )}
      </div>
    </div>
  )
}
