import type { AuthResponse } from './types'

const SESSION_KEY = 'highvibe.session'

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function loadSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    return raw ? (JSON.parse(raw) as AuthResponse) : null
  } catch {
    return null
  }
}

export function saveSession(session: AuthResponse | null) {
  try {
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify(session))
    else localStorage.removeItem(SESSION_KEY)
  } catch {
    /* storage unavailable: the session lives only in memory */
  }
  sessionListeners.forEach((listener) => listener(session))
}

type SessionListener = (session: AuthResponse | null) => void
const sessionListeners = new Set<SessionListener>()
export function onSessionChange(listener: SessionListener) {
  sessionListeners.add(listener)
  return () => {
    sessionListeners.delete(listener)
  }
}

let refreshing: Promise<boolean> | null = null

/** Exchanges the refresh token for a new pair; concurrent callers share one request. */
export async function refreshSession(): Promise<boolean> {
  const session = loadSession()
  if (!session?.refreshToken) return false
  refreshing ??= (async () => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      })
      if (!res.ok) {
        saveSession(null)
        return false
      }
      saveSession((await res.json()) as AuthResponse)
      return true
    } catch {
      return false
    } finally {
      refreshing = null
    }
  })()
  return refreshing
}

async function errorFrom(res: Response): Promise<ApiError> {
  let message = res.statusText || 'Request failed'
  try {
    const body = await res.json()
    if (body?.errors?.length) {
      message = body.errors.map((e: { field: string; message: string }) => `${e.field} ${e.message}`).join(', ')
    } else if (body?.message) {
      message = body.message
    }
  } catch {
    /* body was not JSON */
  }
  return new ApiError(res.status, message)
}

/** fetch with the bearer token and one transparent refresh on 401. */
export async function authFetch(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const session = loadSession()
  const headers = new Headers(init.headers)
  if (session?.token) headers.set('Authorization', `Bearer ${session.token}`)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(path, { ...init, headers })
  if (res.status === 401 && retry && session?.refreshToken && (await refreshSession())) {
    return authFetch(path, init, false)
  }
  if (!res.ok) throw await errorFrom(res)
  return res
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await authFetch(path, init)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function publicApi<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw await errorFrom(res)
  return (await res.json()) as T
}
