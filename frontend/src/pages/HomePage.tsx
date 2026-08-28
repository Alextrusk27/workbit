import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { HeroChatDemo } from '@/components/marketing/HeroChatDemo'
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
import { plans, promoActive } from '@/content/plans'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'

const features = [
  {
    icon: <IconRole className="size-5" />,
    title: 'Вопросы под профессию',
    body: 'Навык, профессия и уровень — вопросы подстраиваются под цель.',
  },
  {
    icon: <IconPencil className="size-5" />,
    title: 'Правки на полях',
    body: 'Рецензент отмечает сильное и указывает, что уточнить.',
  },
  {
    icon: <IconChart className="size-5" />,
    title: 'Вероятность оффера',
    body: 'Итоговый фидбэк и оценка шансов — низкая, средняя, высокая.',
  },
  {
    icon: <IconClock className="size-5" />,
    title: 'История сессий',
    body: 'Все интервью сохраняются — следи за прогрессом.',
  },
]

const reportRows = [
  { title: 'Вопрос 4 · Воронка и метрики', score: 5 },
  { title: 'Вопрос 5 · Падение CTR', score: 2 },
  { title: 'Вопрос 6 · Сегментация', score: 4 },
]

const progressPoints = [
  { x: 6, y: 92.5, date: '12 мая' },
  { x: 28, y: 81.3, date: '18 мая' },
  { x: 50, y: 85, date: '26 мая' },
  { x: 72, y: 62.5, date: '2 июн' },
  { x: 94, y: 40, date: '9 июн' },
]

const chartYs = [
  { top: 10, label: '5★' },
  { top: 47.5, label: '4★' },
  { top: 85, label: '3★' },
  { top: 122.5, label: '2★' },
  { top: 160, label: '1★' },
]

const chartLine = progressPoints
  .map((p, i) => `${i === 0 ? 'M' : 'L'}${p.x * 10} ${p.y}`)
  .join(' ')
const chartArea = `${chartLine} L940 160 L60 160 Z`

const trainerSteps = [
  { label: 'Навык', value: 'Медиапланирование' },
  { label: 'Профессия', value: 'Интернет-маркетолог' },
  { label: 'Уровень', value: 'Средний' },
]

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

  return (
    <>
      <header className="glow-hero relative overflow-hidden pt-10 pb-12 sm:pt-24 sm:pb-20">
        <Container className="relative grid grid-cols-[minmax(0,1fr)] items-center gap-9 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)] lg:gap-16">
          <div>
            <h1 className="text-ink text-[clamp(38px,5vw,58px)] leading-[1.08] font-extrabold tracking-[-0.03em]">
              Тренажёр собеседований{' '}
              <span className="text-grad">с AI-интервьюером</span>
            </h1>
            <p className="text-muted mt-5.5 max-w-[48ch] text-lg">
              <strong className="text-ink font-semibold">
                Подготовка к собеседованию онлайн:
              </strong>{' '}
              реалистичные вопросы под твою профессию и уровень, разбор каждого
              ответа и вероятность оффера.
            </p>
            <div className="mt-8">
              <Link to={startTo} className={buttonClasses()}>
                Начать интервью — бесплатно
              </Link>
            </div>
            <p className="text-dim mt-4.5 text-[13.5px]">
              1 интервью и 3 тренировки — бесплатно
            </p>
          </div>

          <HeroChatDemo />
        </Container>
      </header>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title="Симулятор собеседования">
              Всё как на настоящем интервью: от выбора профессии до финального
              вердикта — полный цикл подготовки.
            </SectionHead>
          </Reveal>
          <div className="grid grid-cols-[minmax(0,1fr)] gap-5 sm:grid-cols-2 lg:grid-cols-12">
            <Reveal className="sm:col-span-2 lg:col-span-7">
              <div className="border-indigo/25 bg-grad-plan h-full rounded-2xl border p-[30px] pb-7">
                <div className="border-indigo/25 bg-indigo/12 text-indigo mb-4.5 grid size-11 place-items-center rounded-lg border">
                  <IconLink />
                </div>
                <h3 className="text-ink text-xl font-bold tracking-[-0.01em]">
                  Интервью по вакансии
                </h3>
                <p className="text-muted mt-2 max-w-[52ch] text-[14.5px]">
                  Вставь ссылку на вакансию с hh.ru — тренажёр соберёт сессию
                  под конкретные требования работодателя.
                </p>
                <div className="mt-5">
                  <VacancyUrlForm variant="card" />
                </div>
                <p className="text-dim mt-3.5 text-[13px]">
                  Первое интервью бесплатно ·{' '}
                  <Link
                    to="/ai-interview"
                    className="text-indigo hover:text-violet transition-colors"
                  >
                    Как устроено AI-интервью →
                  </Link>
                </p>
              </div>
            </Reveal>
            <Reveal delay={0.05} className="sm:col-span-2 lg:col-span-5">
              <div className="border-line bg-card h-full rounded-2xl border p-[30px] pb-7">
                <div className="border-indigo/25 bg-indigo/12 text-indigo mb-4.5 grid size-11 place-items-center rounded-lg border">
                  <IconStar />
                </div>
                <h3 className="text-ink text-xl font-bold tracking-[-0.01em]">
                  Оценка каждого ответа
                </h3>
                <p className="text-muted mt-2 text-[14.5px]">
                  Звёзды от 1 до 5 за каждый ответ. В отчёте видно, где просел и
                  что подтянуть.
                </p>
                <div className="border-surface-line bg-surface mt-5 flex flex-col gap-2 rounded-[10px] border px-3.5 py-[13px]">
                  {reportRows.map((r) => (
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
            {features.map((f, i) => (
              <Reveal
                key={f.title}
                delay={(i + 2) * 0.05}
                className="lg:col-span-3"
              >
                <FeatureCard size="sm" icon={f.icon} title={f.title}>
                  {f.body}
                </FeatureCard>
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section id="demo" className="scroll-mt-20 py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title="Тестовое собеседование">
              Отвечай на вопросы AI-интервьюера в диалоговом окне в свободной
              форме.
            </SectionHead>
          </Reveal>

          <div className="grid grid-cols-[minmax(0,1fr)] gap-5 lg:grid-cols-3">
            <Reveal>
              <StepCard n="1" title="Вопрос">
                <ChatBubble
                  role="bot"
                  who="Workbit-интервьюер · 7 / 10 · Маркетолог"
                  className="max-w-full"
                >
                  CTR рекламной кампании упал вдвое при том же бюджете. Как
                  будешь искать причину?
                </ChatBubble>
              </StepCard>
            </Reveal>
            <Reveal delay={0.05}>
              <StepCard n="2" title="Ответ">
                <ChatBubble role="user" who="Ты" className="max-w-full">
                  Сначала посмотрю частоту показов и выгорание креативов, потом
                  разбивку по площадкам и сегментам. Если просело везде
                  равномерно — обновлю креативы и пересоберу аудитории.
                </ChatBubble>
              </StepCard>
            </Reveal>
            <Reveal delay={0.1}>
              <StepCard n="3" title="Разбор">
                <ChatBubble
                  role="bot"
                  who="Разбор рецензента"
                  className="max-w-full"
                >
                  <span className="mb-1.5 flex items-center gap-2 text-[12.5px]">
                    <Stars value={4} />
                    <span className="text-dim">4 из 5</span>
                  </span>
                  Верная логика: частота → площадки → сегменты. Уточни, как
                  отделишь выгорание креатива от выгорания аудитории —
                  интервьюеры спросят про тест.
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
                      Прогресс по вакансии
                    </span>
                  </div>
                  <p className="text-muted mt-2 text-[13.5px]">
                    Интернет-маркетолог · оценка за попытку, 5 последних
                    интервью
                  </p>
                </div>
                <div className="grid w-full grid-cols-3 gap-3 sm:flex sm:w-auto sm:gap-9">
                  <div className={statTile}>
                    <p className="text-ink m-0 flex items-center gap-2 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal">
                      4,2
                      <Stars value={4} className="text-xs" />
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      Лучшая оценка
                    </p>
                  </div>
                  <div className={statTile}>
                    <p className="text-ok m-0 text-[17px] leading-[26px] font-bold tabular-nums sm:text-[19px] sm:leading-normal">
                      +1,2
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      Динамика
                    </p>
                  </div>
                  <div className={statTile}>
                    <p className="m-0 leading-[26px] sm:leading-[29px]">
                      <span className="bg-ok/12 text-ok inline-flex h-5.5 items-center rounded-full px-2.5 text-xs font-semibold">
                        высокая
                      </span>
                    </p>
                    <p className="text-dim mt-[3px] text-[11px] sm:text-xs">
                      Оффер
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
                  {progressPoints.map((p, i) => (
                    <span
                      key={p.date}
                      style={{ left: `${p.x}%`, top: p.y }}
                      className={cn(
                        'absolute -translate-x-1/2 -translate-y-1/2 rounded-full',
                        i === progressPoints.length - 1
                          ? 'bg-indigo ring-indigo/15 size-[11px] ring-4'
                          : 'bg-canvas border-indigo size-[9px] border-[2.5px]',
                      )}
                    />
                  ))}
                  {progressPoints.map((p, i) => (
                    <span
                      key={p.date}
                      style={{ left: `${p.x}%` }}
                      className={cn(
                        'absolute top-[174px] -translate-x-1/2 text-[11px] whitespace-nowrap',
                        i === progressPoints.length - 1
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
                Проходи интервью по вакансии повторно и отслеживай прогресс в
                карточке вакансии.
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
                  Тренажёр навыков
                </h2>
                <p className="text-muted mt-3 max-w-[52ch] text-base">
                  Короткие тренировки по одному навыку: выбери тему, уточни
                  профессией и уровнем — и отрабатывай нужные темы между
                  интервью.
                </p>
                <div className="mt-6">
                  <Link
                    to="/skills-trainer"
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    Попробовать тренажёр
                  </Link>
                </div>
              </div>
              <div className="flex flex-col gap-2.5">
                {trainerSteps.map((s, i) => (
                  <div
                    key={s.label}
                    className="border-surface-line bg-surface flex items-center gap-3 rounded-xl border px-4 py-[13px]"
                  >
                    <StepBadge n={String(i + 1)} className="size-6 text-xs" />
                    <span className="text-ink text-sm font-semibold">
                      {s.label}
                    </span>
                    <span className="text-muted ml-auto text-[13.5px]">
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
            <SectionHead title="Тарифы">
              {promoActive &&
                'До 1 октября к покупке — до 5 интервью в подарок.'}
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

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Следующее собеседование — уже не первое"
              actions={
                <Link to={startTo} className={buttonClasses()}>
                  Начать бесплатно
                </Link>
              }
            >
              Проведи пробное интервью сегодня и приходи на настоящее
              подготовленным.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
