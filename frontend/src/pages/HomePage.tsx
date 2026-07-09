import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { MarginNote } from '@/components/ui/MarginNote'
import { PlanCard } from '@/components/ui/PlanCard'
import { plans } from '@/content/plans'
import { faqPreview } from '@/content/faq'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'

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

interface Example {
  meta: string
  question: string
  answer: string
  score: number
  note: string
}

const examples: Example[] = [
  {
    meta: 'Вопрос 3 / 10 · Java-разработчик · Middle',
    question: 'Чем отличается HashMap от ConcurrentHashMap?',
    answer:
      'HashMap не потокобезопасен, а ConcurrentHashMap разрешает конкурентный доступ и блокирует не всю таблицу, а сегменты, чтобы чтение и запись шли параллельно.',
    score: 4,
    note: 'Верно про сегменты. Уточните, что в Java 8+ это не сегменты, а блокировка на уровне бакета.',
  },
  {
    meta: 'Вопрос 5 / 12 · Инженер по тестированию · Сбер',
    question: 'Чем smoke-тестирование отличается от регрессионного?',
    answer:
      'Smoke — быстрый прогон ключевых функций после сборки: работает ли вообще. Регрессионное шире: проверяем, что новые изменения не сломали то, что уже работало.',
    score: 5,
    note: 'Суть верна. Добавьте, что smoke гоняют перед допуском к глубоким тестам, а регрессию — по расширенному набору кейсов.',
  },
]

/** Пример вопроса с разбором: примеры сменяются сами раз в 6 с и по клику на точки. */
function ExampleCarousel() {
  const [active, setActive] = useState(0)

  useEffect(() => {
    const id = setInterval(
      () => setActive((v) => (v + 1) % examples.length),
      6000,
    )
    return () => clearInterval(id)
  }, [])

  const ex = examples[active]

  return (
    <figure
      className="border-rule bg-paper-2/50 animate-rise rounded-lg border p-6 sm:p-8"
      style={{ animationDelay: '320ms' }}
    >
      <figcaption className="text-muted mb-4 font-mono text-xs tracking-wide">
        {ex.meta}
      </figcaption>
      <p className="text-ink font-display text-lg leading-snug">
        {ex.question}
      </p>
      <div className="mt-5 grid gap-5 sm:grid-cols-[1fr_auto] sm:items-start">
        <blockquote className="border-rule text-ink/90 text-body-sm border-l-2 pl-4 leading-relaxed">
          {ex.answer}
        </blockquote>
        <MarginNote score={ex.score} className="sm:max-w-[15rem]">
          {ex.note}
        </MarginNote>
      </div>
      <div
        className="mt-6 flex gap-2"
        role="tablist"
        aria-label="Примеры вопросов"
      >
        {examples.map((item, idx) => (
          <button
            key={item.meta}
            type="button"
            role="tab"
            aria-selected={idx === active}
            aria-label={`Пример ${idx + 1}`}
            onClick={() => setActive(idx)}
            className={cn(
              'h-1.5 rounded-full transition-all',
              idx === active
                ? 'bg-accent w-6'
                : 'bg-rule hover:bg-ink/30 w-1.5',
            )}
          />
        ))}
      </div>
    </figure>
  )
}

export function HomePage() {
  usePageTitle()
  const [selectedPlan, setSelectedPlan] = useState(
    () => (plans.find((p) => p.featured) ?? plans[0]).name,
  )
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
              оценка, будто это уже настоящее интервью.
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
      <section id="how" className="scroll-mt-20">
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
      <section className="border-rule border-t">
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
      <section id="faq" className="border-rule scroll-mt-20 border-t">
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
      <section className="border-rule border-t">
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
