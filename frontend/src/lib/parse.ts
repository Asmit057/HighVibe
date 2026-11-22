import type { ChatEvent } from './types'

/**
 * Turns the streamed, tagged assistant text into display events while it is still arriving.
 * Complete tags become events; an unfinished tag at the end is shown as "in progress".
 */
export function parseStreaming(text: string): { events: ChatEvent[]; active: ChatEvent | null } {
  const events: ChatEvent[] = []
  const tag = /<(message|file|tool)(\s[^>]*)?>([\s\S]*?)<\/\1>/gi
  let match: RegExpExecArray | null
  let lastIndex = 0
  let order = 1
  while ((match = tag.exec(text)) !== null) {
    events.push(toEvent(match[1].toLowerCase(), match[2] ?? '', match[3], order++, false))
    lastIndex = tag.lastIndex
  }

  const rest = text.slice(lastIndex)
  const open = /<(message|file|tool)(\s[^>]*)?>([\s\S]*)$/i.exec(rest)
  const active = open ? toEvent(open[1].toLowerCase(), open[2] ?? '', open[3], order, true) : null
  return { events, active }
}

function attr(raw: string, name: string): string | null {
  const m = new RegExp(`${name}\\s*=\\s*"([^"]*)"`).exec(raw)
  return m ? m[1] : null
}

function toEvent(tag: string, attrs: string, body: string, order: number, streaming: boolean): ChatEvent {
  const base = { id: null, sequenceOrder: order, status: streaming ? ('PENDING' as const) : null }
  switch (tag) {
    case 'file':
      return { ...base, type: 'FILE_EDIT', content: body, filePath: attr(attrs, 'path'), metadata: null }
    case 'tool':
      return { ...base, type: 'TOOL_LOG', content: body.trim(), filePath: null, metadata: attr(attrs, 'args') }
    default:
      return { ...base, type: 'MESSAGE', content: body.trim(), filePath: null, metadata: attr(attrs, 'phase') }
  }
}

/** Minimal, safe markdown: **bold**, `code`, line breaks. Returns React-free tokens. */
export type MdToken = { kind: 'text' | 'bold' | 'code' | 'br'; value: string }

export function tokenizeMarkdown(input: string): MdToken[] {
  const tokens: MdToken[] = []
  const lines = input.split('\n')
  lines.forEach((line, index) => {
    const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g
    let last = 0
    let m: RegExpExecArray | null
    while ((m = pattern.exec(line)) !== null) {
      if (m.index > last) tokens.push({ kind: 'text', value: line.slice(last, m.index) })
      const token = m[0]
      tokens.push(token.startsWith('**') ? { kind: 'bold', value: token.slice(2, -2) } : { kind: 'code', value: token.slice(1, -1) })
      last = m.index + token.length
    }
    if (last < line.length) tokens.push({ kind: 'text', value: line.slice(last) })
    if (index < lines.length - 1) tokens.push({ kind: 'br', value: '' })
  })
  return tokens
}
