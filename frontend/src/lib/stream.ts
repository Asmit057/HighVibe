import { authFetch } from './api'
import type { ChatMessage } from './types'

export interface StreamHandlers {
  onDelta: (text: string) => void
  onDone: (message: ChatMessage) => void
  onError: (message: string) => void
}

/**
 * POSTs a prompt and reads the server-sent events of the generation.
 * (EventSource cannot send a bearer token, so the stream is read with fetch.)
 */
export async function streamChat(projectId: number, message: string, handlers: StreamHandlers, signal?: AbortSignal) {
  let res: Response
  try {
    res = await authFetch('/api/chat/stream', {
      method: 'POST',
      body: JSON.stringify({ projectId, message }),
      headers: { Accept: 'text/event-stream' },
      signal,
    })
  } catch (e) {
    handlers.onError(e instanceof Error ? e.message : 'Could not start the generation')
    return
  }

  const reader = res.body?.getReader()
  if (!reader) {
    handlers.onError('Streaming is not supported by this browser')
    return
  }
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false

  const handleEvent = (raw: string) => {
    const data = raw
      .split(/\r?\n/)
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).replace(/^ /, ''))
      .join('\n')
    if (!data) return
    try {
      const event = JSON.parse(data) as { type: string; text?: string; message?: ChatMessage; error?: string }
      if (event.type === 'delta' && event.text) handlers.onDelta(event.text)
      else if (event.type === 'done' && event.message) {
        finished = true
        handlers.onDone(event.message)
      } else if (event.type === 'error') {
        finished = true
        handlers.onError(event.error ?? 'Generation failed')
      }
    } catch {
      /* ignore keep-alive or malformed frames */
    }
  }

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary = buffer.search(/\r?\n\r?\n/)
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(buffer.slice(boundary).match(/^\r?\n\r?\n/)![0].length + boundary)
        handleEvent(frame)
        boundary = buffer.search(/\r?\n\r?\n/)
      }
    }
    if (buffer.trim()) handleEvent(buffer)
  } catch (e) {
    if (!signal?.aborted) handlers.onError(e instanceof Error ? e.message : 'Connection lost')
    return
  }
  if (!finished && !signal?.aborted) handlers.onError('The connection closed before the generation finished')
}
