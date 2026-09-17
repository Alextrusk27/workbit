import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { ChatShell } from '@/components/chat/ChatShell'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { IconChart, IconPencil, IconStar } from '@/components/marketing/icons'
import { VacancyUrlForm } from '@/components/marketing/VacancyUrlForm'
import { aiInterview, type AiInterviewIcon } from '@/content/pages/aiInterview'
import { useAuth } from '@/features/auth/useAuth'
import { ctaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'

const icons: Record<
  AiInterviewIcon,
  { Icon: typeof IconStar; className: string }
> = {
  star: { Icon: IconStar, className: 'text-star' },
  pencil: { Icon: IconPencil, className: 'text-indigo' },
  chart: { Icon: IconChart, className: 'text-cyan' },
}

const { hero, flow, vacancy, feedback, cta } = aiInterview

function ChatDemo() {
  const { demo } = vacancy
  return (
    <ChatShell name={demo.name} status={demo.status}>
      <ChatBubble role="bot" who={demo.question.who}>
        {inline(demo.question.text)}
      </ChatBubble>
      <ChatBubble role="user">{demo.answer.text}</ChatBubble>
      <ChatBubble role="bot" who={demo.review.who}>
        <span className="mb-1.5 flex items-center gap-2 text-[13.5px]">
          <Stars value={demo.review.score} />
          <span className="text-dim">{demo.review.score} из 5</span>
        </span>
        {inline(demo.review.text)}
      </ChatBubble>
    </ChatShell>
  )
}

function ReportDemo() {
  const { demo } = feedback
  return (
    <div className="border-line bg-card shadow-chat overflow-hidden rounded-2xl border">
      <div className="border-divider bg-glass border-b px-5 py-3.5">
        <p className="text-ink text-[15px] font-semibold">{demo.title}</p>
        <p className="text-dim text-xs">{demo.meta}</p>
      </div>
      <div className="flex flex-col gap-3.5 px-5 pt-4.5 pb-5">
        {demo.scores.map((r) => (
          <div
            key={r.title}
            className="flex items-center justify-between gap-3"
          >
            <span className="text-ink text-[13.5px]">{r.title}</span>
            <Stars value={r.score} className="text-[13.5px]" />
          </div>
        ))}
        <div className="bg-surface rounded-md px-3.5 py-2.5">
          <span className="text-dim mb-1 block text-xs font-semibold tracking-[0.05em] uppercase">
            {demo.noteLabel}
          </span>
          <span className="text-ink text-[13.5px] leading-normal">
            {inline(demo.note)}
          </span>
        </div>
        <div className="border-divider border-t pt-3.5">
          <div className="flex items-center justify-between gap-3">
            <span className="text-ink text-[13.5px] font-semibold">
              {demo.offer.label}
            </span>
            <span className="bg-indigo/12 text-indigo inline-flex h-5.5 items-center rounded-full px-2.5 text-xs font-semibold">
              {demo.offer.value}
            </span>
          </div>
          <div className="mt-2.5 flex gap-1">
            {[1, 2, 3].map((i) => (
              <span
                key={i}
                className={
                  i <= demo.offer.level
                    ? 'bg-grad h-1.5 flex-1 rounded-full'
                    : 'bg-line h-1.5 flex-1 rounded-full'
                }
              />
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export function AiInterviewPage() {
  const { isAuthenticated } = useAuth()
  const start = ctaLink(cta.primary, {
    start: '/app/interview/new',
    isAuthenticated,
  })

  return (
    <>
      <PageHero
        title={<HeroTitle hero={hero} />}
        actions={
          <div className="w-full">
            <VacancyUrlForm />
            <p className="text-dim mt-4 text-[13.5px]">{inline(hero.note)}</p>
          </div>
        }
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
              <ChatDemo />

              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  {vacancy.title}
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  {inline(vacancy.body)}
                </p>
                <ul className="mt-5 flex flex-col gap-3 text-left">
                  {vacancy.advantages.map((a) => (
                    <li key={a} className="text-muted flex gap-2.5 text-[15px]">
                      <span aria-hidden className="text-indigo shrink-0">
                        ✓
                      </span>
                      {inline(a)}
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
            <div className="grid items-center gap-16 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)]">
              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  {feedback.title}
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  {inline(feedback.body)}
                </p>
                <ul className="mt-5 flex flex-col gap-4 text-left">
                  {feedback.items.map((r) => {
                    const { Icon, className } = icons[r.icon]
                    return (
                      <li key={r.icon} className="flex gap-3">
                        <Icon
                          className={`${className} mt-0.5 size-[18px] shrink-0`}
                        />
                        <span className="text-muted text-[15px]">
                          {inline(r.body)}
                        </span>
                      </li>
                    )
                  })}
                </ul>
              </div>

              <ReportDemo />
            </div>
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
                    {...start}
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
