import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FaqList } from '@/components/marketing/FaqList'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { HeroChatDemo } from '@/components/marketing/HeroChatDemo'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { VacancyUrlForm } from '@/components/marketing/VacancyUrlForm'
import {
  IconChart,
  IconClock,
  IconLink,
  IconPencil,
  IconRole,
  IconStar,
} from '@/components/marketing/icons'
import { faq as faqItems } from '@/content/faq'
import { home, type HomeIcon } from '@/content/pages/home'
import { plans, promoActive } from '@/content/plans'
import type { Cta } from '@/content/types'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'
import { ctaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'

const icons: Record<HomeIcon, typeof IconRole> = {
  role: IconRole,
  pencil: IconPencil,
  chart: IconChart,
  clock: IconClock,
}

const { hero, simulator, demo, trainer, pricing, faq, cta } = home
const { progress } = demo

const chartYs = [
  { top: 10, label: '5★' },
  { top: 47.5, label: '4★' },
  { top: 85, label: '3★' },
  { top: 122.5, label: '2★' },
  { top: 160, label: '1★' },
]

const chartLine = progress.points
  .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x * 10} ${p.y}`)
  .join(' ')
const [firstPoint] = progress.points
const lastPoint = progress.points[progress.points.length - 1]
const chartArea = `${chartLine} L${lastPoint.x * 10} 160 L${firstPoint.x * 10} 160 Z`

const homeFaq = faqItems.filter((item) => item.home)

const statTile =
  'border-line bg-glass min-w-0 rounded-[10px] border px-3 py-2.5 sm:rounded-none sm:border-0 sm:bg-transparent sm:p-0'

function StepBadge({ n, className }: { n: string; className?: string }) {
  return (
    <span
      className={cn(
        'border-indigo/25 bg-indigo/12 text-indigo grid size-6.5 shrink-0 place-items-center rounded-full border text-[13px] font-bold',
        className,
      )}
    >
      {n}
    </span>
  )
}

function StepCard({
  n,
  title,
  children,
}: {
  n: string
  title: string
  children: ReactNode
}) {
  return (
    <div className="border-line bg-card flex h-full flex-col rounded-2xl border p-6">
      <div className="mb-4 flex items-center gap-2.5">
        <StepBadge n={n} />
        <span className="text-ink text-[15px] font-semibold">{title}</span>
      </div>
      {children}
    </div>
  )
}

export function HomePage() {
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app' : '/login'
  const link = (c: Cta) => ctaLink(c, { start: '/app', isAuthenticated })

  return (
    <>
      <header className="glow-hero relative overflow-hidden pt-10 pb-12 sm:pt-24 sm:pb-20">
        <Container className="relative grid grid-cols-[minmax(0,1fr)] items-center gap-9 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)] lg:gap-16">
          <div>
            <h1 className="text-ink text-[clamp(38px,5vw,58px)] leading-[1.08] font-extrabold tracking-[-0.03em]">
              <HeroTitle hero={hero} />
            </h1>
            <p className="text-muted mt-5.5 max-w-[48ch] text-lg">
              {inline(hero.text)}
            </p>
            <div className="mt-8">
              <Link {...link(hero.cta)} className={buttonClasses()}>
                {hero.cta.label}
              </Link>
            </div>
          </div>

          <HeroChatDemo />
        </Container>
      </header>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title={simulator.title}>
              {inline(simulator.lead)}
            </SectionHead>
          </Reveal>
          <div className="grid grid-cols-[minmax(0,1fr)] gap-5 sm:grid-cols-2 lg:grid-cols-12">
            <Reveal className="sm:col-span-2 lg:col-span-7">
              <div className="border-indigo/25 bg-grad-plan h-full rounded-2xl border p-[30px] pb-7">
                <div className="border-indigo/25 bg-indigo/12 text-indigo mb-4.5 grid size-11 place-items-center rounded-lg border">
                  <IconLink />
                </div>
                <h3 className="text-ink text-xl font-bold tracking-[-0.01em]">
                  {simulator.vacancy.title}
                </h3>
                <p className="text-muted mt-2 max-w-[52ch] text-[14.5px]">
                  {inline(simulator.vacancy.body)}
                </p>
                <div className="mt-5">
                  <VacancyUrlForm variant="card" />
                </div>
                <p className="text-dim mt-3.5 text-[13px]">
                  {inline(simulator.vacancy.note)}
                </p>
              </div>
            </Reveal>
            <Reveal delay={0.05} className="sm:col-span-2 lg:col-span-5">
              <div className="border-line bg-card h-full rounded-2xl border p-[30px] pb-7">
                <div className="border-indigo/25 bg-indigo/12 text-indigo mb-4.5 grid size-11 place-items-center rounded-lg border">
                  <IconStar />
                </div>
                <h3 className="text-ink text-xl font-bold tracking-[-0.01em]">
                  {simulator.scoring.title}
                </h3>
                <p className="text-muted mt-2 text-[14.5px]">
                  {inline(simulator.scoring.body)}
                </p>
                <div className="border-surface-line bg-surface mt-5 flex flex-col gap-2 rounded-[10px] border px-3.5 py-[13px]">
                  {simulator.scoring.rows.map((r) => (
                    <div
                      key={r.title}
                      className="flex items-center justify-between gap-2.5 text-[13px]"
                    >
                      <span className="text-muted">{r.title}</span>
                      <Stars value={r.score} className="text-[13px]" />
                    </div>
                  ))}
                </div>
              </div>
            </Reveal>
            {simulator.features.map((f, i) => {
              const Icon = icons[f.icon]
              return (
                <Reveal
                  key={f.title}
                  delay={(i + 2) * 0.05}
                  className="lg:col-span-3"
                >
                  <FeatureCard
                    size="sm"
                    icon={<Icon className="size-5" />}
                    title={f.title}
                  >
                    {inline(f.body)}
                  </FeatureCard>
                </Reveal>
              )
            })}
          </div>
          <Reveal className="mt-7 flex flex-wrap items-center justify-center gap-2">
            <span className="text-muted mr-1 text-[13.5px]">
              {simulator.professions.label}
            </span>
            {simulator.professions.items.map((p) => (
              <span
                key={p}
                className="border-line text-muted rounded-full border px-3 py-1 text-[13px]"
              >
                {p}
              </span>
            ))}
            <span className="text-dim text-[13.5px]">
              {simulator.professions.tail}
            </span>
          </Reveal>
        </Container>
      </section>

      <section id="demo" className="scroll-mt-20 py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title={demo.title}>{inline(demo.lead)}</SectionHead>
          </Reveal>

          <div className="grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-3">
            <Reveal>
              <StepCard n="1" title={demo.question.title}>
                <ChatBubble
                  role="bot"
                  who={demo.question.who}
                  className="max-w-full"
                >
                  {inline(demo.question.text)}
                </ChatBubble>
              </StepCard>
            </Reveal>
            <Reveal delay={0.05}>
              <StepCard n="2" title={demo.answer.title}>
                <ChatBubble
                  role="user"
                  who={demo.answer.who}
                  className="max-w-full"
                >
                  {demo.answer.text}
                </ChatBubble>
              </StepCard>
            </Reveal>
            <Reveal delay={0.1}>
              <StepCard n="3" title={demo.review.title}>
                <ChatBubble
                  role="bot"
                  who={demo.review.who}
                  className="max-w-full"
                >
                  <span className="mb-1.5 flex items-center gap-2 text-[12.5px]">
                    <Stars value={demo.review.score} />
                    <span className="text-dim">{demo.review.score} из 5</span>
                  </span>
                  {inline(demo.review.text)}
                </ChatBubble>
              </StepCard>
            </Reveal>
          </div>

          <Reveal className="mt-5">
            <div className="border-indigo/25 bg-grad-plan rounded-2xl border p-6 sm:px-7">
              <div className="flex flex-wrap items-start justify-between gap-x-8 gap-y-4">
                <div>
                  <div className="flex items-center gap-2.5">
                    <StepBadge n="4" />
                    <span className="text-ink text-[15px] font-semibold">
                      {progress.title}
                    </span>
                  </div>
                  <p className="text-muted mt-2 text-[13.5px]">
                    {inline(progress.lead)}
                  </p>
                </div>
                <div className="grid w-full grid-cols-3 gap-3 sm:flex sm:w-auto sm:gap-9">
                  <div className={statTile}>
                    <p className="text-ink m-0 flex items-center gap-2 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal">
                      {progress.best.value}
                      <Stars
                        value={progress.best.stars}
                        className="text-xs max-sm:hidden"
                      />
                      <span className="text-star text-xs sm:hidden">★</span>
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      <span className="sm:hidden">
                        {progress.best.shortLabel}
                      </span>
                      <span className="max-sm:hidden">
                        {progress.best.label}
                      </span>
                    </p>
                  </div>
                  <div className={statTile}>
                    <p className="text-ok m-0 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal">
                      {progress.trend.value}
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      {progress.trend.label}
                    </p>
                  </div>
                  <div className={statTile}>
                    <p className="m-0 leading-[26px] sm:leading-[29px]">
                      <span className="bg-ok/12 text-ok inline-flex h-5.5 items-center rounded-full px-2.5 text-xs font-semibold">
                        {progress.offer.value}
                      </span>
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      {progress.offer.label}
                    </p>
                  </div>
                </div>
              </div>

              <div className="mt-7 grid grid-cols-[44px_1fr]">
                <div className="relative h-[170px]">
                  {chartYs.map((y, i) => (
                    <span
                      key={y.label}
                      style={{ top: y.top }}
                      className={cn(
                        'absolute right-2.5 -translate-y-1/2 text-[11px] whitespace-nowrap',
                        i === 0 ? 'text-star' : 'text-dim',
                      )}
                    >
                      {y.label}
                    </span>
                  ))}
                </div>
                <div className="relative mb-6 h-[170px]">
                  {chartYs.map((y, i) => (
                    <div
                      key={y.label}
                      style={{ top: y.top }}
                      className={cn(
                        'border-line absolute right-0 left-0 h-0 border-t',
                        i < chartYs.length - 1 && 'border-dashed',
                      )}
                    />
                  ))}
                  <svg
                    viewBox="0 0 1000 170"
                    preserveAspectRatio="none"
                    className="absolute inset-0 block h-full w-full"
                    aria-hidden="true"
                  >
                    <path d={chartArea} className="fill-indigo/10" />
                    <path
                      d={chartLine}
                      fill="none"
                      className="stroke-indigo"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      vectorEffect="non-scaling-stroke"
                    />
                  </svg>
                  {progress.points.map((p, i) => (
                    <span
                      key={p.date}
                      style={{ left: `${p.x}%`, top: p.y }}
                      className={cn(
                        'absolute -translate-x-1/2 -translate-y-1/2 rounded-full',
                        i === progress.points.length - 1
                          ? 'bg-indigo ring-indigo/15 size-[11px] ring-4'
                          : 'bg-canvas border-indigo size-[9px] border-[2.5px]',
                      )}
                    />
                  ))}
                  {progress.points.map((p, i) => (
                    <span
                      key={p.date}
                      style={{ left: `${p.x}%` }}
                      className={cn(
                        'absolute top-[174px] -translate-x-1/2 text-[11px] whitespace-nowrap',
                        i === progress.points.length - 1
                          ? 'text-indigo font-semibold'
                          : 'text-dim',
                      )}
                    >
                      {p.date}
                    </span>
                  ))}
                </div>
              </div>
              <p className="text-dim mt-3.5 text-[13px]">
                {inline(progress.note)}
              </p>
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <div className="border-line bg-card grid grid-cols-[minmax(0,1fr)] items-center gap-9 rounded-[20px] border px-6 py-8 sm:px-12 sm:py-11 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-12">
              <div>
                <h2 className="text-ink text-[clamp(26px,3vw,32px)]">
                  {trainer.title}
                </h2>
                <p className="text-muted mt-3 max-w-[52ch] text-base">
                  {inline(trainer.body)}
                </p>
                <div className="mt-6">
                  <Link
                    {...link(trainer.cta)}
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    {trainer.cta.label}
                  </Link>
                </div>
              </div>
              <div className="flex flex-col gap-2.5">
                {trainer.steps.map((s, i) => (
                  <div
                    key={s.label}
                    className="border-surface-line bg-surface flex items-center gap-3 rounded-xl border px-4 py-[13px]"
                  >
                    <StepBadge n={String(i + 1)} className="size-6 text-xs" />
                    <span className="text-ink shrink-0 text-sm font-semibold">
                      {s.label}
                    </span>
                    <span className="text-muted ml-auto min-w-0 text-right text-[13.5px]">
                      {s.value}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </Reveal>
        </Container>
      </section>

      <section id="pricing" className="scroll-mt-20 py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title={pricing.title}>
              {inline(
                promoActive
                  ? `${pricing.lead} ${pricing.promoLead}`
                  : pricing.lead,
              )}
            </SectionHead>
          </Reveal>
          <div className="grid justify-center gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {plans.map((p, i) => (
              <Reveal key={p.name} delay={i * 0.05}>
                <PlanCard
                  plan={p}
                  features={p.previewFeatures}
                  to={p.featured ? '/pricing' : startTo}
                />
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title={faq.title}>{inline(faq.lead)}</SectionHead>
          </Reveal>
          <FaqList items={homeFaq} />
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title={cta.title}
              actions={
                <>
                  <Link {...link(cta.primary)} className={buttonClasses()}>
                    {cta.primary.label}
                  </Link>
                  <Link
                    {...link(cta.secondary)}
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    {cta.secondary.label}
                  </Link>
                  <p className="text-dim basis-full text-[13.5px]">
                    {inline(cta.note)}
                  </p>
                </>
              }
            >
              {inline(cta.body)}
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
