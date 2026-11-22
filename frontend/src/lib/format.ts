/** "3 projects", "1 live preview", "Unlimited projects" for a plan limit. */
export function planLimit(n: number | null, unit: string) {
  if (n == null) return `Unlimited ${unit}`
  return `${n.toLocaleString()} ${n === 1 ? unit.replace(/s\b/, '') : unit}`
}
