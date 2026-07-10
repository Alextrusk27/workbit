import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { MarginNote } from '@/components/ui/MarginNote'
import { PlanCard } from '@/components/ui/PlanCard'
import { Stars } from '@/components/ui/Stars'
import { plans } from '@/content/plans'
import { faqPreview } from '@/content/faq'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'
import { useReveal } from '@/lib/useReveal'
import { useTilt } from '@/lib/useTilt'

function Eyebrow({ children }: { children: ReactNode }) {
  return (
    <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
      {children}
    </p>
  )
}

const steps = [
  {
    n: '01',
    title: 'Выберите роль или вакансию',
    body: 'Профессия, уровень и тип компании — или вставьте ссылку на вакансию с hh.ru. Вопросы подстраиваются под то, к чему вы готовитесь.',
  },
  {
    n: '02',
    title: 'Отвечайте как на собеседовании',
    body: 'Вопросы приходят по одному, вы отвечаете текстом. Без подсказок и вариантов — как в реальном разговоре.',
  },
  {
    n: '03',
    title: 'Получайте разбор',
    body: 'Оценка каждого ответа звёздами от 1 до 5, пометки рецензента на полях, итоговый фидбэк и вероятность оффера.',
  },
]

type QaExample = {
  kind: 'qa'
  meta: string
  question: string
  answer: string
  score: number
  note: string
}

type SummaryExample = {
  kind: 'summary'
  meta: string
  avgScore: number
  offerProbability: string
  feedback: string
}

type Example = QaExample | SummaryExample

const examples: Example[] = [
  {
    kind: 'qa',
    meta: 'Вопрос 3 / 10 · Java-разработчик · Middle',
    question: 'Чем отличается HashMap от ConcurrentHashMap?',
    answer:
      'HashMap не потокобезопасен, а ConcurrentHashMap разрешает конкурентный доступ и блокирует не всю таблицу, а сегменты, чтобы чтение и запись шли параллельно.',
    score: 4,
    note: 'Верно про сегменты. Уточните, что в Java 8+ это не сегменты, а блокировка на уровне бакета.',
  },
  {
    kind: 'qa',
    meta: 'Вопрос 5 / 12 · Инженер по тестированию · Сбер',
    question: 'Чем smoke-тестирование отличается от регрессионного?',
    answer:
      'Smoke — быстрый прогон ключевых функций после сборки: работает ли вообще. Регрессионное шире: проверяем, что новые изменения не сломали то, что уже работало.',
    score: 5,
    note: 'Суть верна. Добавьте, что smoke гоняют перед допуском к глубоким тестам, а регрессию — по расширенному набору кейсов.',
  },
  {
    kind: 'summary',
    meta: 'Итог интервью · Java-разработчик · Middle',
    avgScore: 4.2,
    offerProbability: 'Высокая',
    feedback:
      'Сильные ответы по коллекциям и многопоточности, рассуждения структурные. Проседает работа с транзакциями — повторите уровни изоляции и оптимистичные блокировки. В целом вы готовы к собеседованию на Middle.',
  },
]

function CarouselArrow({ dir }: { dir: 'left' | 'right' }) {
  return (
    <svg
      viewBox="0 0 16 16"
      className="size-4"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path
        d={dir === 'left' ? 'M10 3 5 8l5 5' : 'M6 3l5 5-5 5'}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function QuestionExample({ ex }: { ex: QaExample }) {
  return (
    <>
      <p className="text-muted mb-4 font-mono text-xs tracking-wide">
        {ex.meta}
      </p>
      <p className="text-ink font-display text-lg leading-snug">
        {ex.question}
      </p>
      <div className="mt-5 grid gap-5 sm:grid-cols-[1fr_auto] sm:items-start">
        <blockquote className="border-rule text-ink/90 text-body-sm border-l-2 pl-4 leading-relaxed">
          {ex.answer}
        </blockquote>
        <div
          className="sm:max-w-[15rem]"
          style={{
            transform:
              'translate3d(calc(var(--px, 0) * 10px), calc(var(--py, 0) * 10px), 0)',
            transition: 'transform 0.3s ease-out',
          }}
        >
          <MarginNote score={ex.score}>{ex.note}</MarginNote>
        </div>
      </div>
    </>
  )
}

function SummaryExampleCard({ ex }: { ex: SummaryExample }) {
  return (
    <>
      <p className="text-muted mb-4 font-mono text-xs tracking-wide">
        {ex.meta}
      </p>
      <p className="text-ink font-display text-lg leading-snug">
        Итоговый разбор
      </p>
      <div className="mt-5 flex flex-wrap items-center gap-x-6 gap-y-3">
        <span className="flex items-center gap-2">
          <span className="text-accent text-xl">
            <Stars value={ex.avgScore} />
          </span>
          <span className="text-muted font-mono text-sm tabular-nums">
            {ex.avgScore.toFixed(1).replace('.', ',')} из 5
          </span>
        </span>
        <span className="text-muted text-sm">
          Вероятность оффера:{' '}
          <span className="text-ink font-medium">{ex.offerProbability}</span>
        </span>
      </div>
      <div
        className="mt-5"
        style={{
          transform:
            'translate3d(calc(var(--px, 0) * 8px), calc(var(--py, 0) * 8px), 0)',
          transition: 'transform 0.3s ease-out',
        }}
      >
        <MarginNote>{ex.feedback}</MarginNote>
      </div>
    </>
  )
}

/** Примеры разбора: вопросы и итог собеседования. Сменяются плавным кроссфейдом
 *  сами раз в 6 с и стрелками в нижних углах (ручное переключение сбрасывает
 *  таймер — таймаут пересоздаётся при каждой смене активного слайда). Слайды
 *  наложены в одну grid-ячейку — высота карточки не прыгает при переключении. */
function ExampleCarousel() {
  const [active, setActive] = useState(0)
  const count = examples.length

  useEffect(() => {
    const id = setTimeout(() => setActive((v) => (v + 1) % count), 6000)
    return () => clearTimeout(id)
  }, [active, count])

  const go = (dir: 1 | -1) => setActive((v) => (v + dir + count) % count)
  const tiltRef = useTilt<HTMLDivElement>()

  return (
    <div
      className="animate-rise [perspective:1200px]"
      style={{ animationDelay: '320ms' }}
    >
      <div
        ref={tiltRef}
        className="transition-transform duration-300 ease-out will-change-transform"
        style={{
          transform: 'rotateX(var(--rx, 0deg)) rotateY(var(--ry, 0deg))',
        }}
      >
        <figure className="border-rule bg-paper-2/50 relative flex flex-col rounded-lg border p-6 shadow-(--shadow-lift) sm:p-8">
          <div className="grid">
            {examples.map((ex, idx) => (
              <div
                key={ex.meta}
                aria-hidden={idx !== active}
                className={cn(
                  'col-start-1 row-start-1 transition-opacity duration-500 ease-out',
                  idx === active
                    ? 'opacity-100'
                    : 'pointer-events-none opacity-0',
                )}
              >
                {ex.kind === 'qa' ? (
                  <QuestionExample ex={ex} />
                ) : (
                  <SummaryExampleCard ex={ex} />
                )}
              </div>
            ))}
          </div>

          <div className="mt-6 flex items-center justify-between">
            <button
              type="button"
              onClick={() => go(-1)}
              aria-label="Предыдущий пример"
              className="border-rule text-muted hover:border-ink/30 hover:text-ink focus-visible:outline-accent flex size-9 items-center justify-center rounded-full border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              <CarouselArrow dir="left" />
            </button>
            <span className="text-muted font-mono text-xs tabular-nums">
              {active + 1} / {count}
            </span>
            <button
              type="button"
              onClick={() => go(1)}
              aria-label="Следующий пример"
              className="border-rule text-muted hover:border-ink/30 hover:text-ink focus-visible:outline-accent flex size-9 items-center justify-center rounded-full border transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              <CarouselArrow dir="right" />
            </button>
          </div>

          <span
            aria-hidden
            className="pointer-events-none absolute inset-0 rounded-lg transition-opacity duration-300"
            style={{
              opacity: 'var(--active, 0)',
              mixBlendMode: 'soft-light',
              background:
                'radial-gradient(28rem 28rem at var(--mx, 50%) var(--my, 50%), color-mix(in srgb, #fff 45%, transparent), transparent 60%)',
            }}
          />
        </figure>
      </div>
    </div>
  )
}

export function HomePage() {
  usePageTitle()
  const [selectedPlan, setSelectedPlan] = useState(
    () => (plans.find((p) => p.featured) ?? plans[0]).name,
  )
  const how = useReveal<HTMLElement>()
  const pricing = useReveal<HTMLElement>()
  const faq = useReveal<HTMLElement>()
  const cta = useReveal<HTMLElement>()
  return (
    <>
      {/* Hero: продукт показан сразу — ответ соискателя с правкой рецензента. */}
      <section className="border-rule border-b">
        <Container className="grid gap-12 py-16 sm:py-24 lg:grid-cols-[1.05fr_0.95fr] lg:items-center lg:gap-16">
          <div>
            <div className="animate-rise">
              <Eyebrow>AI-интервью для соискателей</Eyebrow>
            </div>
            <h1
              className="text-ink animate-rise mt-5 text-4xl leading-[1.05] sm:text-5xl lg:text-6xl"
              style={{ animationDelay: '80ms' }}
            >
              Готовьтесь до собеседования, а не на нём
            </h1>
            <p
              className="text-ink/85 animate-rise mt-6 max-w-xl text-lg"
              style={{ animationDelay: '160ms' }}
            >
              Тренируйтесь на реалистичных вопросах под вашу профессию и
              уровень. После каждого ответа — разбор рецензента и честная
              оценка, как в настоящем интервью.
            </p>
            <div
              className="animate-rise mt-8 flex flex-wrap items-center gap-3"
              style={{ animationDelay: '240ms' }}
            >
              <Link to="/login" className={buttonClasses({ size: 'lg' })}>
                Начать интервью
              </Link>
              <Link
                to="/#how"
                className={buttonClasses({ variant: 'secondary', size: 'lg' })}
              >
                Как это работает
              </Link>
            </div>
          </div>

          {/* Артефакт-сигнатура: лист с ответом и пометкой на полях. */}
          <ExampleCarousel />
        </Container>
      </section>

      {/* Как проходит интервью — реальная последовательность, отсюда нумерация. */}
      <section
        id="how"
        ref={how.ref}
        className={cn('scroll-mt-20', how.shown ? 'animate-rise' : 'opacity-0')}
      >
        <Container className="py-16 sm:py-24">
          <Eyebrow>Как проходит интервью</Eyebrow>
          <h2 className="text-ink mt-4 max-w-2xl text-3xl sm:text-4xl">
            Три шага от настройки до разбора
          </h2>
          <ol className="mt-12 grid gap-10 sm:grid-cols-3 sm:gap-8">
            {steps.map((s) => (
              <li key={s.n}>
                <span className="text-accent font-mono text-sm tracking-widest">
                  {s.n}
                </span>
                <div className="bg-rule mt-3 mb-4 h-px w-full" />
                <h3 className="text-ink text-xl">{s.title}</h3>
                <p className="text-muted text-body-sm mt-2 leading-relaxed">
                  {s.body}
                </p>
              </li>
            ))}
          </ol>
        </Container>
      </section>

      {/* Превью тарифов */}
      <section
        ref={pricing.ref}
        className={cn(
          'border-rule border-t',
          pricing.shown ? 'animate-rise' : 'opacity-0',
        )}
      >
        <Container className="py-16 sm:py-24">
          <div className="flex items-end justify-between gap-4">
            <div>
              <Eyebrow>Тарифы</Eyebrow>
              <h2 className="text-ink mt-4 text-3xl sm:text-4xl">
                Начните бесплатно
              </h2>
            </div>
            <Link
              to="/pricing"
              className="text-accent hover:text-accent-hover hidden text-sm transition-colors sm:inline"
            >
              Все тарифы →
            </Link>
          </div>

          <div
            role="radiogroup"
            aria-label="Выбор тарифа"
            className="mt-10 grid items-stretch gap-5 sm:grid-cols-2"
          >
            {plans.map((p) => {
              const isSelected = selectedPlan === p.name
              return (
                <PlanCard
                  key={p.name}
                  selected={isSelected}
                  onSelect={() => setSelectedPlan(p.name)}
                  className="p-6 sm:p-8"
                >
                  <div className="flex items-baseline justify-between">
                    <h3 className="text-ink font-display text-xl">{p.name}</h3>
                    {p.featured && (
                      <span className="bg-accent text-paper rounded-sm px-2 py-0.5 font-mono text-xs tracking-wide">
                        Популярный
                      </span>
                    )}
                  </div>
                  <p className="mt-4">
                    <span className="text-ink font-display text-3xl tabular-nums">
                      {p.price}
                    </span>
                    <span className="text-muted ml-2 text-sm">{p.note}</span>
                  </p>
                  <ul className="mt-6 grow space-y-2">
                    {p.previewFeatures.map((f) => (
                      <li
                        key={f}
                        className="text-ink/85 text-body-sm flex gap-2"
                      >
                        <span className="text-accent" aria-hidden>
                          —
                        </span>
                        {f}
                      </li>
                    ))}
                  </ul>
                  <Link
                    to="/login"
                    onClick={(e) => e.stopPropagation()}
                    className={buttonClasses({
                      variant: isSelected ? 'primary' : 'secondary',
                      className: 'mt-6 w-full',
                    })}
                  >
                    {p.cta}
                  </Link>
                </PlanCard>
              )
            })}
          </div>

          <Link
            to="/pricing"
            className="text-accent hover:text-accent-hover mt-8 inline-block text-sm transition-colors sm:hidden"
          >
            Все тарифы →
          </Link>
        </Container>
      </section>

      {/* Превью FAQ */}
      <section
        id="faq"
        ref={faq.ref}
        className={cn(
          'border-rule scroll-mt-20 border-t',
          faq.shown ? 'animate-rise' : 'opacity-0',
        )}
      >
        <Container className="grid gap-10 py-16 sm:py-24 lg:grid-cols-[0.8fr_1.2fr]">
          <div>
            <Eyebrow>Вопросы</Eyebrow>
            <h2 className="text-ink mt-4 text-3xl sm:text-4xl">
              Коротко о главном
            </h2>
            <Link
              to="/faq"
              className={buttonClasses({
                variant: 'secondary',
                className: 'mt-6',
              })}
            >
              Все вопросы
            </Link>
          </div>
          <dl className="divide-rule divide-y">
            {faqPreview.map((item) => (
              <div key={item.q} className="py-5 first:pt-0">
                <dt className="text-ink font-display text-lg">{item.q}</dt>
                <dd className="text-muted text-body-sm mt-2 leading-relaxed">
                  {item.a}
                </dd>
              </div>
            ))}
          </dl>
        </Container>
      </section>

      {/* Финальный CTA */}
      <section
        ref={cta.ref}
        className={cn(
          'border-rule border-t',
          cta.shown ? 'animate-rise' : 'opacity-0',
        )}
      >
        <Container className="py-16 text-center sm:py-24">
          <h2 className="text-ink mx-auto max-w-2xl text-3xl sm:text-4xl">
            Следующее собеседование — уже не первое
          </h2>
          <p className="text-muted mx-auto mt-4 max-w-xl">
            Проведите пробное интервью сегодня и приходите на настоящее
            подготовленным.
          </p>
          <div className="mt-8">
            <Link to="/login" className={buttonClasses({ size: 'lg' })}>
              Начать интервью
            </Link>
          </div>
        </Container>
      </section>
    </>
  )
}
