import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, loadSession, onSessionChange, publicApi, saveSession } from './api'
import type { AuthResponse, UserProfile } from './types'

interface AuthState {
  user: UserProfile | null
  login: (username: string, password: string) => Promise<void>
  signup: (name: string, username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(() => loadSession()?.user ?? null)

  useEffect(() => onSessionChange((session) => setUser(session?.user ?? null)), [])

  const login = useCallback(async (username: string, password: string) => {
    saveSession(await publicApi<AuthResponse>('/api/auth/login', { username, password }))
  }, [])

  const signup = useCallback(async (name: string, username: string, password: string) => {
    saveSession(await publicApi<AuthResponse>('/api/auth/signup', { name, username, password }))
  }, [])

  const logout = useCallback(async () => {
    const refreshToken = loadSession()?.refreshToken
    try {
      await api('/api/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) })
    } catch {
      /* already signed out on the server */
    }
    saveSession(null)
  }, [])

  const value = useMemo(() => ({ user, login, signup, logout }), [user, login, signup, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
