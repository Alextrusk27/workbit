import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { ChatShell } from '@/components/chat/ChatShell'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import {
  IconChart,
  IconLink,
  IconPencil,
  IconStar,
} from '@/components/marketing/icons'
import { useAuth } from '@/features/auth/useAuth'
import { savePendingVacancyUrl } from '@/features/vacancy/pendingVacancy'
import { isHhVacancyUrl } from '@/features/vacancy/useVacancy'

const steps = [
  {
    n: '1',
    title: 'Скопируйте ссылку',
    body: 'Вставьте ссылку на вакансию с hh.ru — тренажёр прочитает требования работодателя и соберёт вопросы под них.',
  },
  {
    n: '2',
    title: 'Отвечайте на вопросы AI-интервьюера',
    body: 'Текстом или голосом. Вопросы приходят по одному, тренажёр ждёт столько, сколько нужно, — и задаёт уточняющие.',
  },
  {
    n: '3',
    title: 'Получите фидбек',
    body: 'Балл за каждый ответ, правки на полях и итоговая вероятность оффера — низкая, средняя или высокая.',
  },
]

const results = [
  {
    icon: <IconStar className="text-star mt-0.5 size-[18px] shrink-0" />,
    lead: 'Балл за каждый ответ.',
    body: 'Звёзды от 1 до 5 — слабые места видно сразу после сессии.',
  },
  {
    icon: <IconPencil className="text-indigo mt-0.5 size-[18px] shrink-0" />,
    lead: 'Правки на полях.',
    body: 'Рецензент отмечает сильное и пишет, что уточнить, — как редактор в вашем тексте.',
  },
  {
    icon: <IconChart className="text-cyan mt-0.5 size-[18px] shrink-0" />,
    lead: 'Вероятность оффера.',
    body: 'Итоговая оценка по сессии: низкая, средняя или высокая.',
  },
]

const advantages = [
  'Вопросы по требованиям из вакансии, а не «в среднем по профессии»',
  'Голосовые ответы — тренируете речь, а не только знания',
  'Сессию можно прервать и продолжить с того же вопроса',
]

const reportScores = [
  { title: 'Вопрос 6 · Расхождения в акте сверки', score: 3 },
  { title: 'Вопрос 7 · НДС с полученного аванса', score: 4 },
]

export function AiInterviewPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const startTo = isAuthenticated ? '/app/interview/new' : '/login'

  const [url, setUrl] = useState('')
  const [showHint, setShowHint] = useState(false)

  const onUrlChange = (value: string) => {
    setUrl(value)
    if (showHint) setShowHint(false)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = url.trim()
    if (trimmed !== '' && !isHhVacancyUrl(trimmed)) {
      setShowHint(true)
      return
    }
    if (trimmed !== '') savePendingVacancyUrl(trimmed)
    navigate(startTo, { state: { from: { pathname: '/app/interview/new' } } })
  }

  return (
    <>
      <PageHero
        title={
          <>
            Собеседование с нейросетью
            <br />
            <span className="text-grad">по вашей вакансии</span>
          </>
        }
        actions={
          <div className="w-full">
            <form
              noValidate
              onSubmit={onSubmit}
              className="border-line bg-card shadow-pop mx-auto flex max-w-160 flex-col gap-2.5 rounded-[14px] border p-2 sm:flex-row sm:items-center sm:pl-4.5"
            >
              <span className="flex min-w-0 items-center gap-2.5 px-2.5 pt-1.5 sm:flex-1 sm:px-0 sm:pt-0">
                <IconLink className="text-dim size-[18px] shrink-0" />
                <input
                  type="url"
                  inputMode="url"
                  autoComplete="off"
                  placeholder="https://hh.ru/vacancy/123456"
                  aria-label="Ссылка на вакансию hh.ru"
                  aria-invalid={showHint || undefined}
                  aria-describedby={showHint ? 'hero-vacancy-hint' : undefined}
                  value={url}
                  onChange={(e) => onUrlChange(e.target.value)}
                  className="placeholder:text-dim text-ink w-full min-w-0 bg-transparent text-left text-[15px] outline-none"
                />
              </span>
              <button type="submit" className={buttonClasses()}>
                Пройти пробное интервью
              </button>
            </form>
            {showHint && (
              <p id="hero-vacancy-hint" className="text-dim mt-3 text-[12.5px]">
                Вставьте прямую ссылку на вакансию hh.ru вида
                https://hh.ru/vacancy/123456.
              </p>
            )}
            <p className="text-dim mt-4 text-[13.5px]">
              Первое интервью бесплатно ·{' '}
              <Link
                to="/pricing"
                className="text-indigo hover:text-violet transition-colors"
              >
                Смотреть тарифы
              </Link>
            </p>
          </div>
        }
      >
        <strong className="text-ink font-semibold">
          Пробное интервью по ссылке с hh.ru:
        </strong>{' '}
        полная сессия из вопросов под требования вакансии.
      </PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title="Собеседование с ИИ по шагам">
              Диалог в чате: вопросы приходят по одному, отвечаете текстом или
              голосом.
            </SectionHead>
          </Reveal>
          <div className="relative">
            <span
              aria-hidden
              className="absolute top-6 right-[16.67%] left-[16.67%] hidden h-0.5 bg-[linear-gradient(90deg,rgba(99,102,241,0.12),rgba(139,92,246,0.55))] lg:block"
            />
            <div className="relative grid gap-10 lg:grid-cols-3 lg:gap-8">
              {steps.map((s, i) => (
                <Reveal key={s.n} delay={i * 0.05} className="text-center">
                  <div className="flex justify-center">
                    <span className="border-indigo/30 bg-pop grid size-12 place-items-center rounded-full border text-[19px] font-extrabold shadow-[0_4px_14px_rgba(99,102,241,0.12)]">
                      <span className="bg-[image:var(--grad-btn)] bg-clip-text text-transparent">
                        {s.n}
                      </span>
                    </span>
                  </div>
                  <h3 className="text-ink mt-4.5 text-[17px] font-semibold tracking-[-0.01em]">
                    {s.title}
                  </h3>
                  <p className="text-muted mx-auto mt-2 max-w-[38ch] text-[14.5px]">
                    {s.body}
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
              <ChatShell name="AI-интервьюер" status="интервью по вакансии">
                <ChatBubble role="bot" who="Вопрос 7 / 10 · Электрик">
                  На объекте регулярно выбивает автомат на одной линии. Как
                  будете искать причину?
                </ChatBubble>
                <ChatBubble role="user">
                  Сначала уточню, при какой нагрузке срабатывает, потом отключу
                  линию и померю сопротивление изоляции по участкам…
                </ChatBubble>
                <ChatBubble role="bot" who="Разбор в отчёте">
                  <span className="mb-1.5 flex items-center gap-2 text-[13.5px]">
                    <Stars value={4} />
                    <span className="text-dim">4 из 5</span>
                  </span>
                  Верная последовательность. Уточните, как отличите перегрузку
                  от утечки на землю.
                </ChatBubble>
              </ChatShell>

              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  Интервью по конкретной вакансии
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  Вставьте ссылку на вакансию — тренажёр разберёт требования и
                  соберёт вопросы под обязанности, инструменты и уровень
                  позиции. По сути это мок-интервью: вы репетируете именно то
                  собеседование, на которое идёте.
                </p>
                <ul className="mt-5 flex flex-col gap-3 text-left">
                  {advantages.map((a) => (
                    <li key={a} className="text-muted flex gap-2.5 text-[15px]">
                      <span aria-hidden className="text-indigo shrink-0">
                        ✓
                      </span>
                      {a}
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
                  Что входит в фидбек
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  После сессии вы получаете отчёт — как ревью от старшего
                  коллеги: видно, что дожать до собеседования и каковы шансы
                  сейчас.
                </p>
                <ul className="mt-5 flex flex-col gap-4 text-left">
                  {results.map((r) => (
                    <li key={r.lead} className="flex gap-3">
                      {r.icon}
                      <span className="text-muted text-[15px]">
                        <strong className="text-ink font-semibold">
                          {r.lead}
                        </strong>{' '}
                        {r.body}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>

              <div className="border-line bg-card shadow-chat overflow-hidden rounded-2xl border">
                <div className="border-divider bg-glass border-b px-5 py-3.5">
                  <p className="text-ink text-[15px] font-semibold">
                    Отчёт по интервью
                  </p>
                  <p className="text-dim text-xs">
                    Бухгалтер · 10 вопросов · 42 минуты
                  </p>
                </div>
                <div className="flex flex-col gap-3.5 px-5 pt-4.5 pb-5">
                  {reportScores.map((r) => (
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
                      Правка на полях
                    </span>
                    <span className="text-ink text-[13.5px] leading-normal">
                      Хорошо про счёт-фактуру на аванс. Уточните срок её
                      выставления и когда НДС принимают к вычету.
                    </span>
                  </div>
                  <div className="border-divider border-t pt-3.5">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-ink text-[13.5px] font-semibold">
                        Вероятность оффера
                      </span>
                      <span className="bg-indigo/12 text-indigo inline-flex h-5.5 items-center rounded-full px-2.5 text-xs font-semibold">
                        средняя
                      </span>
                    </div>
                    <div className="mt-2.5 flex gap-1">
                      <span className="bg-grad h-1.5 flex-1 rounded-full" />
                      <span className="bg-grad h-1.5 flex-1 rounded-full" />
                      <span className="bg-line h-1.5 flex-1 rounded-full" />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Пробное собеседование — бесплатно"
              actions={
                <div className="flex flex-col items-center">
                  <Link
                    to={startTo}
                    className={buttonClasses({ className: 'px-7' })}
                  >
                    Начать интервью
                  </Link>
                  <p className="text-dim mt-4 text-[13.5px]">
                    А для отдельных тем —{' '}
                    <Link
                      to="/skills-trainer"
                      className="text-indigo hover:text-violet transition-colors"
                    >
                      тренажёр навыков →
                    </Link>
                  </p>
                </div>
              }
            >
              3 тренировки и пробное AI-интервью бесплатно.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
