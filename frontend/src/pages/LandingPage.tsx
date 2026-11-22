import { ArrowRight, Check, Code2, Eye, Layers, MessageSquareText, Rocket, ShieldCheck, Users, Zap } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import Logo from '../components/Logo'
import MiniAppMock from '../components/MiniAppMock'
import Navbar from '../components/Navbar'
import PromptBox, { IDEAS } from '../components/PromptBox'
import { gradientFor, loadPendingPrompt, savePendingPrompt } from '../lib/create'
import { planLimit } from '../lib/format'
import type { Plan } from '../lib/types'

const STEPS = [
  { icon: MessageSquareText, title: 'Describe it', text: 'Tell HighVibe what you want in plain words. No setup, no boilerplate.' },
  { icon: Zap, title: 'Watch it build', text: 'The AI plans, writes every file and streams its work into the chat live.' },
  { icon: Rocket, title: 'Ship it', text: 'Your app runs instantly in a live preview. Refine it by chatting, or download the code.' },
]

const FEATURES = [
  { icon: Eye, title: 'Live preview', text: 'Every project runs as a real dev server that hot-reloads the moment a file changes.' },
  { icon: Code2, title: 'Real code you own', text: 'React, TypeScript and Tailwind. Edit any file in the browser or download it as a zip.' },
  { icon: Users, title: 'Built for teams', text: 'Invite editors and viewers. Roles decide who can change code and who can only look.' },
  { icon: Zap, title: 'Streaming AI', text: 'Answers appear word by word, with a card for every file the AI writes.' },
  { icon: ShieldCheck, title: 'Secure by design', text: 'Short-lived JWTs, rotating refresh tokens and per-project permissions on every call.' },
  { icon: Layers, title: 'Microservice backend', text: 'Spring Boot services behind a gateway, with Kafka, PostgreSQL, Redis and MinIO.' },
]

const STACK = ['React', 'TypeScript', 'Tailwind', 'Spring Boot', 'Spring Cloud', 'Kafka', 'PostgreSQL', 'Redis', 'MinIO', 'Google Gemini', 'Stripe', 'Docker']

const FALLBACK_PLANS: Plan[] = [
  { id: 1, name: 'Free', priceCents: 0, price: 'Free', maxProjects: 3, maxTokensPerDay: 50000, maxPreviews: 1, unlimitedAi: false, purchasable: false },
  { id: 2, name: 'Pro', priceCents: 2500, price: '$25/mo', maxProjects: 25, maxTokensPerDay: 1000000, maxPreviews: 3, unlimitedAi: false, purchasable: true },
  { id: 3, name: 'Business', priceCents: 5000, price: '$50/mo', maxProjects: null, maxTokensPerDay: null, maxPreviews: 10, unlimitedAi: true, purchasable: true },
]


export default function LandingPage() {
  const navigate = useNavigate()
  const [prompt, setPrompt] = useState(loadPendingPrompt)
  const [plans, setPlans] = useState<Plan[]>(FALLBACK_PLANS)

  useEffect(() => {
    fetch('/api/billing/plans')
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((p: Plan[]) => p.length && setPlans(p))
      .catch(() => { /* keep the defaults */ })
  }, [])

  const start = () => {
    savePendingPrompt(prompt.trim())
    navigate('/signup', { state: { from: '/' } })
  }

  const pickIdea = (idea: string) => {
    setPrompt(idea)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  return (
    <div className="min-h-full">
      <Navbar />

      {/* ------------------------------------------------------------ hero */}
      <section className="relative overflow-hidden">
        <div className="hv-aurora" aria-hidden="true" />
        <div className="hv-grid" aria-hidden="true" />
        <div className="relative mx-auto max-w-3xl px-6 pt-20 pb-24 text-center sm:pt-28">
          <span className="hv-fade-up inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-3 py-1 text-xs text-zinc-300">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" /> AI app builder · live previews · real code
          </span>
          <h1 className="hv-fade-up mt-6 text-5xl font-extrabold tracking-tight sm:text-7xl" style={{ animationDelay: '80ms' }}>
            Build apps by <br className="hidden sm:block" />
            <span className="hv-gradient-text">just describing them</span>
          </h1>
          <p className="hv-fade-up mx-auto mt-5 max-w-xl text-lg text-zinc-400" style={{ animationDelay: '160ms' }}>
            Type an idea. HighVibe writes a complete React app, runs it live next to the chat, and keeps improving it with you.
          </p>
          <div className="hv-fade-up mt-10" style={{ animationDelay: '240ms' }}>
            <PromptBox value={prompt} onChange={setPrompt} onSubmit={start} />
          </div>
          <p className="mt-4 text-xs text-zinc-500">Free plan included · no credit card needed</p>
        </div>
      </section>

      {/* ------------------------------------------------------------ stack strip */}
      <section className="border-y border-white/5 bg-white/[0.02]">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-8 gap-y-3 px-6 py-6 text-sm font-medium text-zinc-500">
          {STACK.map((s) => <span key={s} className="transition hover:text-zinc-200">{s}</span>)}
        </div>
      </section>

      {/* ------------------------------------------------------------ how it works */}
      <section id="how-it-works" className="mx-auto max-w-6xl scroll-mt-20 px-6 py-24">
        <p className="text-center text-sm font-semibold tracking-widest text-violet-300 uppercase">How it works</p>
        <h2 className="mt-3 text-center text-3xl font-bold tracking-tight sm:text-4xl">From idea to running app in minutes</h2>
        <div className="mt-14 grid gap-6 md:grid-cols-3">
          {STEPS.map(({ icon: Icon, title, text }, i) => (
            <div key={title} className="hv-card relative p-6">
              <span className="absolute top-5 right-5 text-5xl font-extrabold text-white/5">{i + 1}</span>
              <span className="grid h-11 w-11 place-items-center rounded-xl bg-gradient-to-br from-brand/30 to-brand-2/20 text-violet-200 ring-1 ring-white/10">
                <Icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <h3 className="mt-5 text-lg font-semibold">{title}</h3>
              <p className="mt-2 text-sm leading-relaxed text-zinc-400">{text}</p>
            </div>
          ))}
        </div>
      </section>

      {/* ------------------------------------------------------------ ideas */}
      <section className="relative mx-auto max-w-6xl px-6 pb-24">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="text-sm font-semibold tracking-widest text-violet-300 uppercase">Start from an idea</p>
            <h2 className="mt-3 text-3xl font-bold tracking-tight sm:text-4xl">What will you build?</h2>
          </div>
          <p className="max-w-sm text-sm text-zinc-400">Pick one to put it in the prompt box, then make it yours.</p>
        </div>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {IDEAS.map((idea, i) => (
            <button key={idea} onClick={() => pickIdea(idea)}
              className="group hv-card overflow-hidden text-left transition hover:-translate-y-1 hover:border-violet-400/40">
              <div className="h-36"><MiniAppMock gradient={gradientFor(i)} variant={i} /></div>
              <div className="flex items-center justify-between gap-3 p-4">
                <span className="text-sm font-medium text-zinc-200">{idea}</span>
                <ArrowRight className="h-4 w-4 shrink-0 text-zinc-500 transition group-hover:translate-x-1 group-hover:text-violet-300" aria-hidden="true" />
              </div>
            </button>
          ))}
        </div>
      </section>

      {/* ------------------------------------------------------------ features */}
      <section className="border-t border-white/5 bg-gradient-to-b from-white/[0.02] to-transparent">
        <div className="mx-auto max-w-6xl px-6 py-24">
          <p className="text-center text-sm font-semibold tracking-widest text-violet-300 uppercase">Features</p>
          <h2 className="mt-3 text-center text-3xl font-bold tracking-tight sm:text-4xl">Everything you need to go from prompt to product</h2>
          <div className="mt-14 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map(({ icon: Icon, title, text }) => (
              <div key={title} className="hv-card p-6 transition hover:border-white/20">
                <Icon className="h-6 w-6 text-violet-300" aria-hidden="true" />
                <h3 className="mt-4 font-semibold">{title}</h3>
                <p className="mt-2 text-sm leading-relaxed text-zinc-400">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* ------------------------------------------------------------ pricing */}
      <section id="pricing" className="mx-auto max-w-6xl scroll-mt-20 px-6 py-24">
        <p className="text-center text-sm font-semibold tracking-widest text-violet-300 uppercase">Pricing</p>
        <h2 className="mt-3 text-center text-3xl font-bold tracking-tight sm:text-4xl">Start free, upgrade when you grow</h2>
        <div className="mt-14 grid items-stretch gap-6 md:grid-cols-3">
          {plans.map((plan) => {
            const popular = plan.name === 'Pro'
            return (
              <div key={plan.id} className={popular ? 'hv-gradient-border p-[1px]' : ''}>
                <div className={`flex h-full flex-col rounded-[1.2rem] p-7 ${popular ? 'bg-ink-900' : 'hv-card'}`}>
                  <div className="flex items-center justify-between">
                    <h3 className="text-lg font-semibold">{plan.name}</h3>
                    {popular && <span className="rounded-full bg-violet-500/20 px-2.5 py-0.5 text-xs font-medium text-violet-200">Most popular</span>}
                  </div>
                  <p className="mt-4 text-4xl font-extrabold">{plan.price.replace('/mo', '')}<span className="text-base font-medium text-zinc-500">{plan.priceCents ? ' /month' : ''}</span></p>
                  <ul className="mt-6 flex-1 space-y-3 text-sm text-zinc-300">
                    <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" />{planLimit(plan.maxProjects, 'projects')}</li>
                    <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" />{plan.unlimitedAi ? 'Unlimited AI' : planLimit(plan.maxTokensPerDay, 'AI tokens a day')}</li>
                    <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" />{planLimit(plan.maxPreviews, 'live previews')}</li>
                    <li className="flex gap-2"><Check className="h-4 w-4 text-emerald-400" aria-hidden="true" />Team sharing and code download</li>
                  </ul>
                  <Link to="/signup" className={`mt-8 rounded-full py-2.5 text-center text-sm font-semibold transition ${popular ? 'bg-white text-ink-950 hover:shadow-lg hover:shadow-white/20' : 'border border-white/15 text-zinc-200 hover:bg-white/5'}`}>
                    {plan.priceCents ? `Choose ${plan.name}` : 'Start for free'}
                  </Link>
                </div>
              </div>
            )
          })}
        </div>
      </section>

      {/* ------------------------------------------------------------ final call to action */}
      <section className="px-6 pb-24">
        <div className="relative mx-auto max-w-5xl overflow-hidden rounded-3xl border border-white/10 bg-gradient-to-br from-violet-600/30 via-fuchsia-600/20 to-rose-600/30 px-8 py-16 text-center">
          <div className="hv-grid" aria-hidden="true" />
          <h2 className="relative text-3xl font-bold tracking-tight sm:text-4xl">Your next app is one sentence away</h2>
          <p className="relative mx-auto mt-3 max-w-lg text-zinc-300">Create a free account and turn your idea into a live app today.</p>
          <Link to="/signup" className="relative mt-8 inline-flex items-center gap-2 rounded-full bg-white px-6 py-3 font-semibold text-ink-950 transition hover:shadow-xl hover:shadow-white/20">
            Get started free <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        </div>
      </section>

      <footer className="border-t border-white/5">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-4 px-6 py-8 text-sm text-zinc-500">
          <Logo />
          <span>Describe it. Generate it. Ship it.</span>
          <a href="https://github.com/Asmit057/HighVibe" target="_blank" rel="noreferrer" className="hover:text-zinc-200">GitHub</a>
        </div>
      </footer>
    </div>
  )
}
