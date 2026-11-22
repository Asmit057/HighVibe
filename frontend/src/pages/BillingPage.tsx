import { Check, CheckCircle2, Loader2, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import Navbar from '../components/Navbar'
import { api } from '../lib/api'
import { planLimit } from '../lib/format'
import type { Plan, Subscription, UsageToday } from '../lib/types'


function UsageRing({ percent }: { percent: number }) {
  const radius = 42
  const circumference = 2 * Math.PI * radius
  return (
    <svg viewBox="0 0 100 100" className="h-28 w-28 -rotate-90" role="img" aria-label={`${percent}% of today's AI tokens used`}>
      <defs>
        <linearGradient id="ring" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#8b5cf6" />
          <stop offset="1" stopColor="#f43f5e" />
        </linearGradient>
      </defs>
      <circle cx="50" cy="50" r={radius} fill="none" stroke="rgba(255,255,255,0.08)" strokeWidth="9" />
      <circle cx="50" cy="50" r={radius} fill="none" stroke="url(#ring)" strokeWidth="9" strokeLinecap="round"
        strokeDasharray={circumference} strokeDashoffset={circumference * (1 - percent / 100)} className="transition-all duration-700" />
    </svg>
  )
}

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
    <div className="min-h-full">
      <Navbar />
      <div className="relative overflow-hidden">
        <div className="hv-aurora opacity-50" aria-hidden="true" />
        <main className="relative mx-auto max-w-5xl px-6 pt-14 pb-24">
          <h1 className="text-4xl font-extrabold tracking-tight">Plans <span className="hv-gradient-text">&amp; usage</span></h1>
          <p className="mt-2 text-zinc-400">See what you used today and pick the plan that fits.</p>

          {params.get('checkout') === 'success' && (
            <p className="mt-6 flex items-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-2.5 text-sm text-emerald-200">
              <CheckCircle2 className="h-4 w-4" aria-hidden="true" /> Your plan was updated.
            </p>
          )}
          {error && <p role="alert" className="mt-6 rounded-xl border border-rose-500/30 bg-rose-500/10 px-4 py-2.5 text-sm text-rose-200">{error}</p>}

          {usage && (
            <section className="hv-card mt-8 flex flex-wrap items-center gap-8 p-6">
              {!usage.unlimited ? (
                <div className="relative">
                  <UsageRing percent={usedPct} />
                  <span className="absolute inset-0 grid place-items-center text-xl font-bold">{usedPct}%</span>
                </div>
              ) : (
                <div className="grid h-28 w-28 place-items-center rounded-full bg-gradient-to-br from-brand/30 to-brand-2/20 ring-1 ring-white/10">
                  <Sparkles className="h-8 w-8 text-violet-200" aria-hidden="true" />
                </div>
              )}
              <div className="min-w-0 flex-1">
                <p className="text-xs font-semibold tracking-widest text-zinc-500 uppercase">Today</p>
                <p className="mt-1 text-2xl font-bold">{usage.planName} plan</p>
                <p className="mt-1 text-sm text-zinc-400">
                  {usage.unlimited
                    ? `${usage.tokensUsed.toLocaleString()} AI tokens used · unlimited`
                    : `${usage.tokensUsed.toLocaleString()} of ${usage.tokenLimit?.toLocaleString()} AI tokens used`}
                  {' · '}{usage.generations} generation{usage.generations === 1 ? '' : 's'}
                </p>
                {subscription?.currentPeriodEnd && (
                  <p className="mt-1 text-xs text-zinc-500">Renews {new Date(subscription.currentPeriodEnd).toLocaleDateString()}</p>
                )}
              </div>
              {subscription?.billingPortalAvailable && (
                <button onClick={openPortal} className="rounded-full border border-white/15 px-4 py-2 text-sm text-zinc-200 hover:bg-white/5">Manage billing</button>
              )}
            </section>
          )}

          <section className="mt-10 grid items-stretch gap-6 md:grid-cols-3">
            {plans.map((plan) => {
              const current = subscription?.plan.id === plan.id
              const popular = plan.name === 'Pro'
              return (
                <div key={plan.id} className={popular ? 'hv-gradient-border p-[1px]' : ''}>
                  <article className={`flex h-full flex-col rounded-[1.2rem] p-7 ${popular ? 'bg-ink-900' : 'hv-card'} ${current ? 'ring-2 ring-emerald-400/50' : ''}`}>
                    <div className="flex items-center justify-between">
                      <h3 className="text-lg font-semibold">{plan.name}</h3>
                      {current
                        ? <span className="rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs font-medium text-emerald-200">Current</span>
                        : popular && <span className="rounded-full bg-violet-500/20 px-2.5 py-0.5 text-xs font-medium text-violet-200">Most popular</span>}
                    </div>
                    <p className="mt-4 text-4xl font-extrabold">{plan.price.replace('/mo', '')}<span className="text-base font-medium text-zinc-500">{plan.priceCents ? ' /month' : ''}</span></p>
                    <ul className="mt-6 flex-1 space-y-3 text-sm text-zinc-300">
                      <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" /> {planLimit(plan.maxProjects, 'projects')}</li>
                      <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" /> {plan.unlimitedAi ? 'Unlimited AI' : planLimit(plan.maxTokensPerDay, 'AI tokens / day')}</li>
                      <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" /> {planLimit(plan.maxPreviews, 'live previews')}</li>
                    </ul>
                    <button disabled={current || busyPlan !== null} onClick={() => choose(plan)}
                      className={`mt-8 flex items-center justify-center gap-2 rounded-full py-2.5 text-sm font-semibold transition disabled:cursor-default ${
                        current ? 'bg-white/5 text-zinc-400' : popular ? 'bg-white text-ink-950 hover:shadow-lg hover:shadow-white/20' : 'border border-white/15 text-zinc-200 hover:bg-white/5'}`}>
                      {busyPlan === plan.id && <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />}
                      {current ? 'Your plan' : plan.purchasable ? `Upgrade to ${plan.name}` : `Switch to ${plan.name}`}
                    </button>
                  </article>
                </div>
              )
            })}
          </section>
        </main>
      </div>
    </div>
  )
}
