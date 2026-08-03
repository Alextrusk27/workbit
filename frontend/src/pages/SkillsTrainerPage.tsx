import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { ChatShell } from '@/components/chat/ChatShell'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { IconClock, IconChart, IconRole } from '@/components/marketing/icons'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

const steps = [
  {
    n: '1',
    title: 'Навык и профессия',
    body: 'Назовите навык и профессию, в контексте которой он нужен: «Hibernate → Java-разработчик». Вопросы будут именно по навыку, а не «в среднем по профессии».',
  },
  {
    n: '2',
    title: 'Уровень сложности',
    body: 'Базовый, начинающий, уверенный или продвинутый — от выбранной планки зависит, насколько глубоко копают вопросы.',
  },
  {
    n: '3',
    title: 'Разбор в конце',
    body: 'Во время тренировки ничего не отвлекает. Балл за каждый ответ и правки рецензента приходят одним отчётом после.',
  },
]

const skills = [
  'Spring Boot',
  'Многопоточность',
  'SQL и базы данных',
  'Коллекции',
  'Системный дизайн',
  'Алгоритмы',
]

const advantages = [
  'Один навык за сессию — глубже, чем «обо всём понемногу»',
  'Разбор не прерывает поток — читаете его в конце',
  'История тренировок показывает прогресс по каждому навыку',
]

const audience = [
  {
    icon: <IconRole />,
    title: 'Роль — любая',
    body: 'Разработчики, аналитики, бухгалтеры, юристы, маркетологи — подсказки из справочника или своя формулировка.',
  },
  {
    icon: <IconClock />,
    title: 'Короткий формат',
    body: 'До десяти вопросов за сессию — удобно тренироваться каждый день, а не раз в неделю по три часа.',
  },
  {
    icon: <IconChart />,
    title: 'Прогресс по навыкам',
    body: 'Все тренировки сохраняются — видно, какой навык подтянулся, а куда стоит вернуться.',
  },
]

export function SkillsTrainerPage() {
  usePageTitle('Тренажёр навыков')
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app/training/new' : '/register'

  return (
    <>
      <PageHero
        badge="Режим «Тренировка навыка»"
        title={
          <>
            Качайте <span className="text-grad">слабые темы</span>, а не всё
            подряд
          </>
        }
        actions={
          <>
            <Link to={startTo} className={buttonClasses()}>
              Начать тренировку
            </Link>
            <Link
              to="/ai-interview"
              className={buttonClasses({ variant: 'secondary' })}
            >
              Или полное интервью
            </Link>
          </>
        }
      >
        Короткие сессии по одному навыку: Spring Boot, многопоточность, SQL —
        что угодно. Вопросы подберёт рецензент, разбор придёт в конце
        тренировки.
      </PageHero>

      <section className="py-22">
        <Container>
          <Reveal>
            <SectionHead title="Тренировка за три клика">
              Навык, профессия, уровень — и вопросы уже идут.
            </SectionHead>
          </Reveal>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {steps.map((s, i) => (
              <Reveal key={s.n} delay={i * 0.05}>
                <FeatureCard icon={s.n} title={s.title}>
                  {s.body}
                </FeatureCard>
              </Reveal>
            ))}
          </div>

          <Reveal className="mt-9 text-center">
            <p className="text-dim mb-3.5 text-[13.5px]">
              Например, такие навыки
            </p>
            <ul className="flex flex-wrap justify-center gap-2">
              {skills.map((s) => (
                <li
                  key={s}
                  className="border-line text-muted rounded-full border px-4 py-[7px] text-[13.5px] font-medium"
                >
                  {s}
                </li>
              ))}
            </ul>
          </Reveal>
        </Container>
      </section>

      <section className="pb-22">
        <Container>
          <Reveal>
            <div className="grid items-center gap-16 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)]">
              <div>
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  Чем тренировка отличается от интервью
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base">
                  Интервью — это репетиция под конкретную вакансию с вердиктом и
                  вероятностью оффера. Тренировка — точечная работа над навыком:
                  короче, спокойнее и без давления «финального решения».
                </p>
                <ul className="mt-5 flex flex-col gap-3">
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

              <ChatShell
                name="Тренировка · Многопоточность"
                status="вопрос 4 из 8"
              >
                <ChatBubble role="bot" who="Вопрос 4 / 8">
                  Что произойдёт, если два потока одновременно вызовут put() у
                  одного HashMap?
                </ChatBubble>
                <ChatBubble role="user">
                  Возможна потеря записей или зацикливание при ресайзе —
                  структура не потокобезопасна…
                </ChatBubble>
                <ChatBubble role="bot" who="Тренажёр">
                  Принято. Разбор всех ответов придёт в конце тренировки.
                </ChatBubble>
              </ChatShell>
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-22">
        <Container>
          <Reveal>
            <SectionHead title="Для любой профессии">
              Не только IT — тренажёр собирает вопросы под любую роль из
              справочника или вашу собственную.
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

      <section className="pb-22">
        <Container>
          <Reveal>
            <CtaPanel
              title="Слабый навык не исчезнет сам"
              actions={
                <>
                  <Link to={startTo} className={buttonClasses()}>
                    Начать тренировку
                  </Link>
                  <Link
                    to="/faq"
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    Вопросы о формате
                  </Link>
                </>
              }
            >
              Первая тренировка займёт немного времени. Начните с того навыка,
              которого боитесь на собеседовании.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
