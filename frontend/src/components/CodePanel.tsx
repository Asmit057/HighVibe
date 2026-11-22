import { File, Folder, Loader2, Save } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { api } from '../lib/api'
import type { FileNode } from '../lib/types'

interface CodePanelProps {
  projectId: number
  files: FileNode[]
  canEdit: boolean
  refreshKey: number
}

interface TreeRow {
  depth: number
  name: string
  path: string
  isFile: boolean
}

function buildRows(files: FileNode[]): TreeRow[] {
  const rows: TreeRow[] = []
  const seen = new Set<string>()
  for (const file of [...files].sort((a, b) => a.path.localeCompare(b.path))) {
    const parts = file.path.split('/')
    parts.forEach((part, i) => {
      const path = parts.slice(0, i + 1).join('/')
      if (seen.has(path)) return
      seen.add(path)
      rows.push({ depth: i, name: part, path, isFile: i === parts.length - 1 })
    })
  }
  return rows
}

export default function CodePanel({ projectId, files, canEdit, refreshKey }: CodePanelProps) {
  const rows = useMemo(() => buildRows(files), [files])
  const [selected, setSelected] = useState<string | null>(null)
  const [content, setContent] = useState('')
  const [original, setOriginal] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!selected && files.length) {
      setSelected(files.find((f) => f.path === 'src/App.tsx')?.path ?? files[0].path)
    }
  }, [files, selected])

  useEffect(() => {
    if (!selected) return
    let cancelled = false
    setLoading(true)
    setError(null)
    api<{ path: string; content: string }>(`/api/projects/${projectId}/files/content?path=${encodeURIComponent(selected)}`)
      .then((res) => {
        if (cancelled) return
        setContent(res.content)
        setOriginal(res.content)
      })
      .catch((e) => !cancelled && setError(e instanceof Error ? e.message : 'Could not open the file'))
      .finally(() => !cancelled && setLoading(false))
    return () => { cancelled = true }
  }, [projectId, selected, refreshKey])

  const save = async () => {
    if (!selected) return
    setSaving(true)
    setError(null)
    try {
      await api(`/api/projects/${projectId}/files/content`, { method: 'PUT', body: JSON.stringify({ path: selected, content }) })
      setOriginal(content)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the file')
    } finally {
      setSaving(false)
    }
  }

  const dirty = content !== original
  return (
    <div className="flex h-full">
      <nav className="hv-scroll w-60 shrink-0 overflow-y-auto border-r border-white/10 py-2 text-sm" aria-label="Project files">
        {rows.map((row) => (
          <button key={row.path} disabled={!row.isFile} onClick={() => setSelected(row.path)}
            style={{ paddingLeft: 12 + row.depth * 14 }}
            className={`flex w-full items-center gap-1.5 py-1 pr-2 text-left font-mono text-xs ${row.path === selected ? 'bg-violet-500/20 text-white' : 'text-zinc-400 hover:bg-white/5'} disabled:cursor-default disabled:hover:bg-transparent`}>
            {row.isFile ? <File className="h-3.5 w-3.5 shrink-0" aria-hidden="true" /> : <Folder className="h-3.5 w-3.5 shrink-0 text-zinc-500" aria-hidden="true" />}
            <span className="truncate">{row.name}</span>
          </button>
        ))}
      </nav>
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-2 border-b border-white/10 px-3 py-2 text-xs">
          <span className="truncate font-mono text-zinc-400">{selected}</span>
          {dirty && <span className="text-amber-300">unsaved</span>}
          {canEdit && (
            <button onClick={save} disabled={!dirty || saving}
              className="ml-auto flex items-center gap-1.5 rounded-md bg-white/10 px-2.5 py-1 hover:bg-white/20 disabled:opacity-40">
              {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />} Save
            </button>
          )}
        </div>
        {error && <p role="alert" className="bg-rose-500/10 px-3 py-2 text-xs text-rose-300">{error}</p>}
        {loading ? (
          <div className="grid flex-1 place-items-center"><Loader2 className="h-5 w-5 animate-spin text-zinc-500" /></div>
        ) : (
          <textarea
            aria-label={`Contents of ${selected ?? 'file'}`}
            value={content}
            onChange={(e) => setContent(e.target.value)}
            readOnly={!canEdit}
            spellCheck={false}
            className="hv-scroll flex-1 resize-none bg-ink-950 p-4 font-mono text-[13px] leading-relaxed text-zinc-200 outline-none"
          />
        )}
      </div>
    </div>
  )
}
