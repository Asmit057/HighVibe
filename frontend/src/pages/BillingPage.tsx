import { Check, Loader2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Navbar from '../components/Navbar'
import { api } from '../lib/api'
import type { Plan, Subscription, UsageToday } from '../lib/types'

const fmt = (n: number | null, unit: string) => (n == null ? `Unlimited ${unit}` : `${n.toLocaleString()} ${unit}`)

export default function BillingPage() {
  const [params] = useSearchParams()
  const [plans, setPlans] = useState<Plan[]>([])
  const [subscription, setSubscription] = useState<Subscription | null>(null)
  const [usage, setUsage] = useState<UsageToday | null>(null)
  const [busyPlan, setBusyPlan] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = async () => {
    try {
      const [p, s, u] = await Promise.all([
        api<Plan[]>('/api/billing/plans'),
        api<Subscription>('/api/billing/subscription'),
        api<UsageToday>('/api/usage/today'),
      ])
      setPlans(p)
      setSubscription(s)
      setUsage(u)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load billing')
    }
  }

  useEffect(() => { void load() }, [])

  const choose = async (plan: Plan) => {
    setBusyPlan(plan.id)
    setError(null)
    try {
      const res = await api<{ checkoutUrl: string; mock: boolean }>('/api/billing/checkout', {
        method: 'POST',
        body: JSON.stringify({ planId: plan.id }),
      })
      if (res.mock) await load()
      else window.location.href = res.checkoutUrl
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Checkout failed')
    } finally {
      setBusyPlan(null)
    }
  }

  const openPortal = async () => {
    try {
      const res = await api<{ portalUrl: string }>('/api/billing/portal', { method: 'POST' })
      window.location.href = res.portalUrl
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not open the billing portal')
    }
  }

  const usedPct = usage && usage.tokenLimit ? Math.min(100, Math.round((usage.tokensUsed / usage.tokenLimit) * 100)) : 0

  return (
    <div className="hv-glow min-h-full">
      <Navbar />
      <main className="mx-auto max-w-5xl px-6 pb-24 pt-10">
        <h1 className="text-3xl font-semibold">Plans & usage</h1>
        {params.get('checkout') === 'success' && (
          <p className="mt-4 rounded-lg bg-emerald-500/10 px-4 py-2 text-sm text-emerald-300">Your plan was updated.</p>
        )}
        {error && <p role="alert" className="mt-4 rounded-lg bg-rose-500/10 px-4 py-2 text-sm text-rose-300">{error}</p>}

        {usage && (
          <section className="mt-8 rounded-2xl border border-white/10 bg-ink-900/70 p-6">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="font-medium">Today on the {usage.planName} plan</h2>
              <span className="text-sm text-zinc-400">{usage.generations} generation(s)</span>
            </div>
            <p className="mt-2 text-sm text-zinc-400">
              {usage.unlimited
                ? `${usage.tokensUsed.toLocaleString()} AI tokens used · unlimited`
                : `${usage.tokensUsed.toLocaleString()} of ${usage.tokenLimit?.toLocaleString()} AI tokens used`}
            </p>
            {!usage.unlimited && (
              <div className="mt-3 h-2 overflow-hidden rounded-full bg-white/10" role="progressbar" aria-valuenow={usedPct} aria-valuemin={0} aria-valuemax={100}>
                <div className="h-full rounded-full bg-gradient-to-r from-brand to-brand-2" style={{ width: `${usedPct}%` }} />
              </div>
            )}
            {subscription?.billingPortalAvailable && (
              <button onClick={openPortal} className="mt-4 text-sm text-violet-300 hover:text-violet-200">Manage billing</button>
            )}
          </section>
        )}

        <section className="mt-8 grid gap-5 md:grid-cols-3">
          {plans.map((plan) => {
            const current = subscription?.plan.id === plan.id
            return (
              <article key={plan.id} className={`flex flex-col rounded-2xl border p-6 ${current ? 'border-violet-400/60 bg-violet-500/10' : 'border-white/10 bg-ink-900/70'}`}>
                <h3 className="text-lg font-semibold">{plan.name}</h3>
                <p className="mt-1 text-3xl font-semibold">{plan.price}</p>
                <ul className="mt-5 flex-1 space-y-2 text-sm text-zinc-300">
                  <li className="flex gap-2"><Check className="h-4 w-4 text-violet-300" aria-hidden="true" /> {fmt(plan.maxProjects, 'projects')}</li>
                  <li className="flex gap-2"><Check className="h-4 w-4 text-violet-300" aria-hidden="true" /> {plan.unlimitedAi ? 'Unlimited AI' : fmt(plan.maxTokensPerDay, 'AI tokens / day')}</li>
                  <li className="flex gap-2"><Check className="h-4 w-4 text-violet-300" aria-hidden="true" /> {fmt(plan.maxPreviews, 'live previews')}</li>
                </ul>
                <button disabled={current || busyPlan !== null} onClick={() => choose(plan)}
                  className="mt-6 flex items-center justify-center gap-2 rounded-lg bg-white py-2 font-medium text-ink-950 disabled:bg-white/10 disabled:text-zinc-400">
                  {busyPlan === plan.id && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                  {current ? 'Current plan' : plan.purchasable ? `Upgrade to ${plan.name}` : `Switch to ${plan.name}`}
                </button>
              </article>
            )
          })}
        </section>
      </main>
    </div>
  )
}
