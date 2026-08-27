import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Stars } from '@/components/ui/Stars'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { TrainingConstructor } from '@/components/marketing/TrainingConstructor'
import { IconClock, IconChart, IconRole } from '@/components/marketing/icons'
import { useAuth } from '@/features/auth/useAuth'

const steps: { n: string; title: string; body: ReactNode }[] = [
  {
    n: '1',
    title: 'Собери тренировку в три клика',
    body: 'Профессия, навык и уровень сложности — без заполнения анкеты. «Бухгалтер → Налоговый учёт»: вопросы будут по навыку в контексте роли.',
  },
  {
    n: '2',
    title: 'Отвечай на вопросы',
    body: (
      <>
        Давай ответы <strong className="text-ink font-semibold">текстом</strong>{' '}
        или <strong className="text-ink font-semibold">голосом</strong> по
        порядку на 10 вопросов. Если вопросов не хватило — можно продлить.
      </>
    ),
  },
  {
    n: '3',
    title: 'Получи отчёт',
    body: 'Балл за каждый ответ и правки рецензента приходят одним отчётом в конце. Результат сохраняется в истории тренировок.',
  },
]

const reportCases = [
  {
    q: '4. НДС с полученного аванса',
    score: 4,
    note: 'Верно про счёт-фактуру на аванс. Уточни срок её выставления и когда НДС принимают к вычету.',
  },
  {
    q: '5. Расхождения в акте сверки',
    score: 3,
    note: 'Порядок сверки верный, но упущена проверка периодов отражения документов.',
  },
]

const advantages = [
  { lead: 'Один навык за сессию', rest: ' — глубже, чем «обо всём понемногу»' },
  { lead: 'Эталонный ответ ИИ', rest: ' — на любой вопрос' },
  {
    lead: 'Слабые места видно сразу',
    rest: ' — понятно, что подтянуть до собеседования',
  },
  {
    lead: 'История тренировок хранит оценки',
    rest: ' — видно, что пройдено и с каким результатом',
  },
]

const startSteps = [
  {
    n: '1',
    lead: 'Пройди AI-интервью по вакансии',
    rest: ' — как репетицию настоящего собеседования.',
  },
  {
    n: '2',
    lead: 'Открой отчёт',
    rest: ' — в нём оценка по каждому навыку, слабые видно сразу.',
  },
  {
    n: '3',
    lead: 'Забирай слабые навыки в тренажёр',
    rest: ' — по одному, пока оценка не вырастет.',
  },
]

const vacancySkills = [
  { name: 'Контекстная реклама', score: 4 },
  { name: 'Email-маркетинг', score: 4 },
]

const audience = [
  {
    icon: <IconRole />,
    title: 'Любая профессия',
    body: 'Разработчики, аналитики, бухгалтеры, юристы, маркетологи — подсказки из справочника или своя формулировка.',
  },
  {
    icon: <IconClock />,
    title: 'Короткий формат',
    body: 'Десять вопросов за подход — удобно тренироваться каждый день. Не хватило — можно добавить.',
  },
  {
    icon: <IconChart />,
    title: 'История тренировок',
    body: 'Удобная история тренировок. Можно посмотреть список всех тренировок и их оценки.',
  },
]

const demoTrainTo = `/app/training/new?${new URLSearchParams({
  skill: 'Веб-аналитика',
  level: 'MIDDLE',
}).toString()}`

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
          <p className="text-ink text-[14.5px] font-semibold">
            Отчёт · Налоговый учёт
          </p>
          <p className="text-ok flex items-center gap-1.5 text-xs">
            <span aria-hidden className="bg-ok size-1.5 rounded-full" />
            10 из 10 ответов разобраны
          </p>
        </div>
      </div>
      <div className="flex flex-col gap-2.5 p-3.5">
        {reportCases.map((c) => (
          <div
            key={c.q}
            className="border-line rounded-xl border px-3.5 py-2.5"
          >
            <p className="text-ink flex flex-wrap items-center justify-between gap-x-2.5 gap-y-1 text-[13.5px] font-semibold">
              {c.q}
              <span className="flex items-center gap-1.5 font-normal">
                <Stars value={c.score} className="text-[13px]" />
                <span className="text-dim text-[13px]">{c.score} из 5</span>
              </span>
            </p>
            <p className="text-dim mt-1.5 text-[10.5px] font-semibold tracking-[0.05em] uppercase">
              Правка рецензента
            </p>
            <p className="text-muted mt-0.5 text-[13.5px]">{c.note}</p>
          </div>
        ))}
        <div className="bg-indigo/8 flex items-center justify-between gap-3 rounded-xl px-3.5 py-2.5">
          <span className="min-w-0">
            <span className="text-ink block text-[13.5px] font-semibold">
              Итог тренировки
            </span>
            <span className="text-dim mt-0.5 block text-xs">
              средний балл за 10 ответов, сохранён в истории
            </span>
          </span>
          <span className="text-indigo text-[13.5px] font-bold whitespace-nowrap">
            3,8 из 5
          </span>
        </div>
      </div>
    </div>
  )
}

function ReferenceDemo() {
  return (
    <div className="border-line bg-card shadow-chat mx-auto max-w-220 overflow-hidden rounded-2xl border">
      <div className="border-divider bg-glass flex flex-wrap items-center justify-between gap-x-4 gap-y-2 border-b px-5 py-3.5 sm:px-6">
        <span className="text-dim text-[13px] font-semibold">
          Вопрос 4 из 10 · Учёт НДС
        </span>
        <span className="border-indigo/35 bg-indigo/8 text-indigo inline-flex h-7.5 items-center gap-1.5 rounded-[9px] border px-3 text-[12.5px] font-semibold">
          <svg
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
            className="size-3.5"
          >
            <path d="M12 2l2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z" />
          </svg>
          Эталонный ответ
        </span>
      </div>
      <div className="flex flex-col gap-4 px-5 pt-5 pb-6 sm:px-6">
        <p className="text-ink text-[15.5px] font-semibold">
          НДС с полученного аванса — как начислить и что происходит при
          отгрузке?
        </p>
        <div className="border-indigo/18 bg-indigo/6 rounded-[14px] border px-4.5 py-4">
          <p className="text-indigo text-[10.5px] font-semibold tracking-[0.05em] uppercase">
            Эталон · сгенерирован ИИ
          </p>
          <p className="text-muted mt-2 text-sm leading-relaxed">
            При получении аванса продавец начисляет НДС по расчётной ставке
            20/120 и в течение 5 календарных дней выставляет авансовый
            счёт-фактуру. При отгрузке НДС начисляется со всей стоимости
            отгрузки, а налог с аванса принимается к вычету — двойного
            налогообложения не возникает.
          </p>
          <div aria-hidden className="mt-3.5 flex items-center gap-2.5">
            <span className="text-dim text-[13px]">
              Эталонный ответ полезен?
            </span>
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

function VacancyDemo({
  trainTo,
  trainState,
}: {
  trainTo: string
  trainState: unknown
}) {
  return (
    <div className="border-line bg-card shadow-chat rounded-xl border px-6 py-5.5">
      <p className="text-ink text-[17px] font-semibold tracking-[-0.01em]">
        Интернет-маркетолог
      </p>
      <p className="text-muted mt-1 text-[13.5px]">
        Агентство «Медиаполе» · Казань
      </p>
      <div className="text-dim mt-3 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 text-[12.5px]">
        <span className="flex items-center gap-2">
          Лучший результат: <Stars value={3.4} />
          <span className="text-ink font-semibold tabular-nums">3,4</span>
        </span>
        <span>
          Оффер: <span className="text-indigo font-semibold">Средняя</span>
        </span>
        <span>Пройдено: 1 раз</span>
      </div>
      <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[12.5px]">
        <span className="text-indigo">Вакансия на hh.ru ↗</span>
        <span className="text-ok font-semibold">Активна</span>
        <span>Опыт: 3–6 лет</span>
      </div>
      <div className="border-divider mt-4 border-t pt-3.5">
        <p className="text-dim text-[11px] font-semibold tracking-[0.06em] uppercase">
          Оценки по навыкам из отчёта
        </p>
        <div className="mt-2.5 flex flex-col gap-2">
          {vacancySkills.map((s) => (
            <div
              key={s.name}
              className="text-ink flex items-center justify-between text-[13.5px]"
            >
              <span>{s.name}</span>
              <Stars value={s.score} />
            </div>
          ))}
          <div className="border-indigo/35 bg-indigo/6 -mx-3 flex items-center justify-between gap-3 rounded-[10px] border px-3 py-2 text-[13.5px]">
            <span className="text-ink font-semibold">Веб-аналитика</span>
            <span className="flex items-center gap-3">
              <Stars value={2} />
              <Link
                to={trainTo}
                state={trainState}
                className="text-indigo hover:text-violet text-[12.5px] font-semibold whitespace-nowrap transition-colors"
              >
                Тренировать →
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
  const startTo = isAuthenticated ? '/app/training/new' : '/login'
  const demoTo = isAuthenticated ? demoTrainTo : '/login'
  const demoState = { from: { pathname: demoTrainTo } }

  return (
    <>
      <PageHero
        title={
          <>
            Тренажёр вопросов для собеседования{' '}
            <span className="text-grad">по навыкам</span>
          </>
        }
        actions={<TrainingConstructor />}
      >
        <strong className="text-ink font-semibold">
          Вопросы как на реальном собеседовании
        </strong>{' '}
        — короткие сессии по десять вопросов.
      </PageHero>

      <section className="py-10 sm:py-16">
        <Container>
          <Reveal>
            <SectionHead title="Как проходит тренировка">
              Короткая сессия из десяти вопросов с возможностью продления
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
              <ReportDemo />

              <div className="max-lg:text-center">
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  Итоговый разбор
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base max-lg:mx-auto">
                  После тренировки приходит один отчёт: балл за каждый ответ и
                  правки рецензента — что верно, а что упущено.
                </p>
                <ul className="mt-5 flex flex-col gap-3 text-left">
                  {advantages.map((a) => (
                    <li
                      key={a.lead}
                      className="text-muted flex gap-2.5 text-[15px]"
                    >
                      <span aria-hidden className="text-indigo shrink-0">
                        ✓
                      </span>
                      <span>
                        <strong className="text-ink font-semibold">
                          {a.lead}
                        </strong>
                        {a.rest}
                      </span>
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
            <SectionHead title="Эталонный ответ ИИ">
              Посмотри, как может выглядеть идеальный ответ
            </SectionHead>
          </Reveal>
          <Reveal>
            <ReferenceDemo />
            <p className="text-dim mt-5 text-center text-sm">
              Доступен по запросу на любой вопрос — во время тренировки или в
              итоговом разборе. На оценки это не влияет
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
                  С чего начать?
                </h2>
                <div className="mt-6 flex flex-col gap-4.5 text-left max-lg:mx-auto max-lg:max-w-[46ch]">
                  {startSteps.map((s) => (
                    <div key={s.n} className="flex items-start gap-3.5">
                      <span className="bg-indigo/10 text-indigo grid size-7.5 shrink-0 place-items-center rounded-[9px] text-sm font-bold">
                        {s.n}
                      </span>
                      <p className="text-muted text-[15.5px] leading-normal">
                        <strong className="text-ink font-semibold">
                          {s.lead}
                        </strong>
                        {s.rest}
                      </p>
                    </div>
                  ))}
                </div>
                <p className="mt-6 text-[15px]">
                  <Link
                    to="/ai-interview"
                    className="text-indigo hover:text-violet font-semibold transition-colors"
                  >
                    Пройти AI-интервью по вакансии →
                  </Link>
                </p>
              </div>

              <VacancyDemo trainTo={demoTo} trainState={demoState} />
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <SectionHead title="Подойдёт для всех">
              Тренажёр подготовит актуальные вопросы для любой профессии с
              подходящим уровнем сложности
            </SectionHead>
          </Reveal>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {audience.map((a, i) => (
              <Reveal key={a.title} delay={i * 0.05}>
                <FeatureCard icon={a.icon} title={a.title}>
                  {a.body}
                </FeatureCard>
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Стань лучшим кандидатом"
              actions={
                <div className="flex flex-col items-center">
                  <Link
                    to={startTo}
                    state={{ from: { pathname: '/app/training/new' } }}
                    className={buttonClasses({ className: 'px-7' })}
                  >
                    Начать тренировку
                  </Link>
                  <p className="text-dim mt-4 text-[13.5px]">
                    3 тренировки бесплатно ·{' '}
                    <Link
                      to="/faq"
                      className="text-indigo hover:text-violet transition-colors"
                    >
                      Вопросы о формате
                    </Link>
                  </p>
                </div>
              }
            >
              Каждая тренировка — шаг к офферу мечты
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
