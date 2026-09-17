import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Stars } from '@/components/ui/Stars'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { TrainingConstructor } from '@/components/marketing/TrainingConstructor'
import { IconClock, IconChart, IconRole } from '@/components/marketing/icons'
import {
  skillsTrainer,
  type SkillsTrainerIcon,
} from '@/content/pages/skillsTrainer'
import type { Cta } from '@/content/types'
import { useAuth } from '@/features/auth/useAuth'
import { ctaLink, type CtaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'

const icons: Record<SkillsTrainerIcon, typeof IconRole> = {
  role: IconRole,
  clock: IconClock,
  chart: IconChart,
}

const { hero, flow, report, reference, start, audience, cta } = skillsTrainer

function ThumbUpIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="size-[15px]"
    >
      <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z" />
      <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
    </svg>
  )
}

function ThumbDownIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="size-[15px]"
    >
      <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3z" />
      <path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17" />
    </svg>
  )
}

function ReportDemo() {
  const { demo } = report
  return (
    <div className="border-line bg-card shadow-chat flex flex-col overflow-hidden rounded-[20px] border">
      <div className="border-divider bg-glass flex items-center gap-3 border-b px-4 py-2.5">
        <span
          aria-hidden
          className="bg-grad grid size-8 place-items-center rounded-[9px] text-sm font-bold text-white"
        >
          w
        </span>
        <div>
          <p className="text-ink text-[14.5px] font-semibold">{demo.title}</p>
          <p className="text-ok flex items-center gap-1.5 text-xs">
            <span aria-hidden className="bg-ok size-1.5 rounded-full" />
            {demo.status}
          </p>
        </div>
      </div>
      <div className="flex flex-col gap-2.5 p-3.5">
        {demo.cases.map((c) => (
          <div
            key={c.question}
            className="border-line rounded-xl border px-3.5 py-2.5"
          >
            <p className="text-ink flex flex-wrap items-center justify-between gap-x-2.5 gap-y-1 text-[13.5px] font-semibold">
              {c.question}
              <span className="flex items-center gap-1.5 font-normal">
                <Stars value={c.score} className="text-[13px]" />
                <span className="text-dim text-[13px]">{c.score} из 5</span>
              </span>
            </p>
            <p className="text-dim mt-1.5 text-[10.5px] font-semibold tracking-[0.05em] uppercase">
              {demo.noteLabel}
            </p>
            <p className="text-muted mt-0.5 text-[13.5px]">{c.note}</p>
          </div>
        ))}
        <div className="bg-indigo/8 flex items-center justify-between gap-3 rounded-xl px-3.5 py-2.5">
          <span className="min-w-0">
            <span className="text-ink block text-[13.5px] font-semibold">
              {demo.summary.title}
            </span>
            <span className="text-dim mt-0.5 block text-xs">
              {demo.summary.body}
            </span>
          </span>
          <span className="text-indigo text-[13.5px] font-bold whitespace-nowrap">
            {demo.summary.value}
          </span>
        </div>
      </div>
    </div>
  )
}

function ReferenceDemo() {
  const { demo } = reference
  return (
    <div className="border-line bg-card shadow-chat mx-auto max-w-220 overflow-hidden rounded-2xl border">
      <div className="border-divider bg-glass flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b px-5 py-3.5 sm:px-6">
        <span className="text-dim text-[13px] font-semibold">{demo.who}</span>
        <span className="border-indigo/35 bg-indigo/8 text-indigo inline-flex h-7.5 items-center gap-1.5 rounded-[9px] border px-3 text-[12.5px] font-semibold">
          <svg
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            className="size-3.5"
          >
            <path d="M12 2l2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z" />
          </svg>
          {demo.badge}
        </span>
      </div>
      <div className="flex flex-col gap-4 px-5 pt-5 pb-6 sm:px-6">
        <p className="text-ink text-[15.5px] font-semibold">{demo.question}</p>
        <div className="border-indigo/18 bg-indigo/6 rounded-[14px] border px-4.5 py-4">
          <p className="text-indigo text-[10.5px] font-semibold tracking-[0.05em] uppercase">
            {demo.label}
          </p>
          <p className="text-muted mt-2 text-sm leading-relaxed">
            {demo.answer}
          </p>
          <div aria-hidden className="mt-3.5 flex items-center gap-2.5">
            <span className="text-dim text-[13px]">{demo.vote}</span>
            <span className="border-indigo/45 bg-indigo/14 text-indigo flex size-8 items-center justify-center rounded-md border">
              <ThumbUpIcon />
            </span>
            <span className="border-line text-muted flex size-8 items-center justify-center rounded-md border">
              <ThumbDownIcon />
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

function VacancyDemo({ train }: { train: CtaLink }) {
  const { vacancy } = start
  return (
    <div className="border-line bg-card shadow-chat rounded-xl border px-6 py-5.5">
      <p className="text-ink text-[17px] font-semibold tracking-[-0.01em]">
        {vacancy.title}
      </p>
      <p className="text-muted mt-1 text-[13.5px]">{vacancy.company}</p>
      <div className="text-dim mt-3 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 text-[12.5px]">
        <span className="flex items-center gap-2">
          {vacancy.best.label} <Stars value={vacancy.best.stars} />
          <span className="text-ink font-semibold tabular-nums">
            {vacancy.best.value}
          </span>
        </span>
        <span>
          {vacancy.offer.label}{' '}
          <span className="text-indigo font-semibold">
            {vacancy.offer.value}
          </span>
        </span>
        <span>{vacancy.passed}</span>
      </div>
      <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[12.5px]">
        <span className="text-indigo">{vacancy.link}</span>
        <span className="text-ok font-semibold">{vacancy.status}</span>
        <span>{vacancy.experience}</span>
      </div>
      <div className="border-divider mt-4 border-t pt-3.5">
        <p className="text-dim text-[11px] font-semibold tracking-[0.06em] uppercase">
          {vacancy.skillsLabel}
        </p>
        <div className="mt-2.5 flex flex-col gap-2">
          {vacancy.skills.map((s) => (
            <div
              key={s.name}
              className="text-ink flex items-center justify-between text-[13.5px]"
            >
              <span>{s.name}</span>
              <Stars value={s.score} />
            </div>
          ))}
          <div className="border-indigo/35 bg-indigo/6 -mx-3 flex items-center justify-between gap-3 rounded-[10px] border px-3 py-2 text-[13.5px]">
            <span className="text-ink font-semibold">{vacancy.weak.name}</span>
            <span className="flex items-center gap-3">
              <Stars value={vacancy.weak.score} />
              <Link
                {...train}
                className="text-indigo hover:text-violet text-[12.5px] font-semibold whitespace-nowrap transition-colors"
              >
                {vacancy.weak.cta.label}
              </Link>
            </span>
          </div>
        </div>
      </div>
    </div>
  )
}

export function SkillsTrainerPage() {
  const { isAuthenticated } = useAuth()
  const link = (c: Cta) =>
    ctaLink(c, { start: '/app/training/new', isAuthenticated })

  return (
    <>
      <PageHero
        title={<HeroTitle hero={hero} />}
        actions={<TrainingConstructor />}
      >
        {inline(hero.text)}
      </PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title={flow.title}>{inline(flow.lead)}</SectionHead>
          </Reveal>
          <div className="relative">
            <span
              aria-hidden
              className="absolute top-6 right-[16.67%] left-[16.67%] hidden h-0.5 bg-[linear-gradient(90deg,rgba(99,102,241,0.12),rgba(139,92,246,0.55))] lg:block"
            />
            <div className="relative grid gap-10 lg:grid-cols-3 lg:gap-8">
              {flow.steps.map((s, i) => (
                <Reveal key={s.title} delay={i * 0.05} className="text-center">
                  <div className="flex justify-center">
                    <span className="border-indigo/30 bg-pop grid size-12 place-items-center rounded-full border text-[19px] font-extrabold shadow-[0_4px_14px_rgba(99,102,241,0.12)]">
                      <span className="bg-[image:var(--grad-btn)] bg-clip-text text-transparent">
                        {i + 1}
                      </span>
                    </span>
                  </div>
                  <h3 className="text-ink mt-4.5 text-[17px] font-semibold tracking-[-0.01em]">
                    {s.title}
                  </h3>
                  <p className="text-muted mx-auto mt-2 max-w-[38ch] text-[14.5px]">
                    {inline(s.body)}
                  </p>
                </Reveal>
              ))}
            </div>
          </div>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <div className="grid items-center gap-16 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
              <ReportDemo />

              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  {report.title}
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  {inline(report.body)}
                </p>
                <ul className="mt-5 flex flex-col gap-3 text-left">
                  {report.advantages.map((a) => (
                    <li key={a} className="text-muted flex gap-2.5 text-[15px]">
                      <span aria-hidden className="text-indigo shrink-0">
                        ✓
                      </span>
                      <span>{inline(a)}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <SectionHead title={reference.title}>
              {inline(reference.lead)}
            </SectionHead>
          </Reveal>
          <Reveal>
            <ReferenceDemo />
            <p className="text-dim mt-5 text-center text-sm">
              {inline(reference.note)}
            </p>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <div className="grid items-center gap-16 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  {start.title}
                </h2>
                <div className="mt-6 flex flex-col gap-4.5 text-left max-lg:mx-auto max-lg:max-w-[46ch]">
                  {start.steps.map((s, i) => (
                    <div key={s} className="flex items-start gap-3.5">
                      <span className="bg-indigo/10 text-indigo grid size-7.5 shrink-0 place-items-center rounded-[9px] text-sm font-bold">
                        {i + 1}
                      </span>
                      <p className="text-muted text-[15.5px] leading-normal">
                        {inline(s)}
                      </p>
                    </div>
                  ))}
                </div>
                <p className="mt-6 text-[15px]">
                  <Link
                    {...link(start.link)}
                    className="text-indigo hover:text-violet font-semibold transition-colors"
                  >
                    {start.link.label}
                  </Link>
                </p>
              </div>

              <VacancyDemo train={link(start.vacancy.weak.cta)} />
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <SectionHead title={audience.title}>
              {inline(audience.lead)}
            </SectionHead>
          </Reveal>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {audience.items.map((a, i) => {
              const Icon = icons[a.icon]
              return (
                <Reveal key={a.title} delay={i * 0.05}>
                  <FeatureCard icon={<Icon />} title={a.title}>
                    {inline(a.body)}
                  </FeatureCard>
                </Reveal>
              )
            })}
          </div>
          <Reveal>
            <p className="text-muted mt-7 text-center text-[15px]">
              {inline(audience.note)}
            </p>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title={cta.title}
              actions={
                <div className="flex flex-col items-center">
                  <Link
                    {...link(cta.primary)}
                    className={buttonClasses({ className: 'px-7' })}
                  >
                    {cta.primary.label}
                  </Link>
                  <p className="text-dim mt-4 text-[13.5px]">
                    {inline(cta.note)}
                  </p>
                </div>
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
