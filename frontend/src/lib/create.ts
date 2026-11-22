import { api } from './api'
import type { Project } from './types'

export const PENDING_PROMPT_KEY = 'highvibe.pendingPrompt'

const LEADING = new Set(['a', 'an', 'the', 'build', 'create', 'make', 'generate', 'design', 'me', 'i', 'want', 'need', 'please', 'simple', 'basic', 'new'])
const ARTICLES = new Set(['a', 'an', 'the'])
const BREAKS = new Set(['with', 'that', 'which', 'where', 'using', 'including', 'and', 'so', 'to'])
const SMALL = new Set(['for', 'of', 'in', 'on', 'to', 'and', 'with', 'a', 'an', 'the'])

/** A short, title-cased project name from the idea, e.g. "Habit Tracker" or "Landing Page for Coffee Roaster". */
export function projectNameFrom(prompt: string) {
  let words = prompt.replace(/[^\p{L}\p{N} ]/gu, ' ').split(/\s+/).filter(Boolean)
  while (words.length > 1 && LEADING.has(words[0].toLowerCase())) words.shift()
  const cut = words.findIndex((w, i) => i >= 2 && BREAKS.has(w.toLowerCase()))
  if (cut > 0) words = words.slice(0, cut)
  words = words.filter((w) => !ARTICLES.has(w.toLowerCase())).slice(0, 6)
  while (words.length > 1 && SMALL.has(words[words.length - 1].toLowerCase())) words.pop()
  if (!words.length) return 'Untitled project'
  return words
    .map((w, i) => (i > 0 && SMALL.has(w.toLowerCase()) ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1)))
    .join(' ')
}

export function createProjectFromPrompt(prompt: string) {
  return api<Project>('/api/projects', {
    method: 'POST',
    body: JSON.stringify({ name: projectNameFrom(prompt), description: prompt }),
  })
}

export function savePendingPrompt(prompt: string | null) {
  try {
    if (prompt) sessionStorage.setItem(PENDING_PROMPT_KEY, prompt)
    else sessionStorage.removeItem(PENDING_PROMPT_KEY)
  } catch {
    /* storage unavailable */
  }
}

export function loadPendingPrompt() {
  try {
    return sessionStorage.getItem(PENDING_PROMPT_KEY) ?? ''
  } catch {
    return ''
  }
}

/** Stable gradient per project, so every card has its own colour. */
const GRADIENTS = [
  'from-violet-500/60 via-fuchsia-500/40 to-rose-500/40',
  'from-sky-500/60 via-indigo-500/40 to-violet-500/40',
  'from-emerald-500/50 via-teal-500/40 to-sky-500/40',
  'from-amber-500/50 via-orange-500/40 to-rose-500/40',
  'from-pink-500/50 via-rose-500/40 to-orange-500/40',
  'from-cyan-500/50 via-sky-500/40 to-blue-600/40',
]
export function gradientFor(id: number) {
  return GRADIENTS[Math.abs(id) % GRADIENTS.length]
}
