import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { ChatShell } from '@/components/chat/ChatShell'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
import { IconChart, IconPencil, IconStar } from '@/components/marketing/icons'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

const steps = [
  {
    n: '1',
    title: 'Соберите сессию',
    body: 'Вставьте ссылку на вакансию с hh.ru — тренажёр прочитает требования работодателя и соберёт вопросы под них.',
  },
  {
    n: '2',
    title: 'Отвечайте своими словами',
    body: 'Текстом или голосом. Вопросы приходят по одному, тренажёр ждёт столько, сколько нужно, — и задаёт уточняющие.',
  },
  {
    n: '3',
    title: 'Получите вердикт',
    body: 'Балл за каждый ответ, правки на полях и итоговая вероятность оффера — низкая, средняя или высокая.',
  },
]

const results = [
  {
    icon: <IconStar />,
    title: 'Балл за каждый ответ',
    body: 'Звёзды от 1 до 5 в отчёте — слабые места видно сразу после сессии.',
  },
  {
    icon: <IconPencil />,
    title: 'Правки на полях',
    body: 'Рецензент отмечает сильное и пишет, что уточнить, — как редактор в вашем тексте.',
  },
  {
    icon: <IconChart />,
    title: 'Вероятность оффера',
    body: 'Итоговый вердикт по сессии: что дожать до собеседования и каковы шансы сейчас.',
  },
]

const advantages = [
  'Вопросы по технологиям из вакансии, а не «в среднем по профессии»',
  'Голосовые ответы — тренируете речь, а не только знания',
  'Сессию можно прервать и продолжить с того же вопроса',
]

export function AiInterviewPage() {
  usePageTitle('AI-интервью')
  const { isAuthenticated } = useAuth()
  const startTo = isAuthenticated ? '/app/interview/new' : '/login'

  return (
    <>
      <PageHero
        badge="Режим «Собеседование»"
        title={
          <>
            Собеседование, которое{' '}
            <span className="text-grad">можно переиграть</span>
          </>
        }
        actions={
          <>
            <Link to={startTo} className={buttonClasses()}>
              Пройти пробное интервью
            </Link>
            <Link
              to="/pricing"
              className={buttonClasses({ variant: 'secondary' })}
            >
              Смотреть тарифы
            </Link>
          </>
        }
      >
        Полная сессия из вопросов под конкретную вакансию с hh.ru. Отвечаете
        текстом или голосом, в конце — вердикт и вероятность оффера.
      </PageHero>

      <section className="py-16">
        <Container>
          <Reveal>
            <SectionHead title="Три шага до вердикта">
              Никаких тестов с вариантами: вопросы приходят по одному, отвечаете
              своими словами.
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
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <div className="grid items-center gap-16 lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
              <ChatShell name="AI-интервьюер" status="интервью по вакансии">
                <ChatBubble
                  role="bot"
                  who="Вопрос 7 / 10 · Senior Java, финтех"
                >
                  Как бы вы спроектировали идемпотентность платёжного API?
                </ChatBubble>
                <ChatBubble role="user">
                  Ключ идемпотентности от клиента, храним результат первой
                  обработки и отдаём его на повторы…
                </ChatBubble>
                <ChatBubble role="bot" who="Разбор в отчёте">
                  <span className="mb-1.5 flex items-center gap-2 text-[13px]">
                    <Stars value={4} />
                    <span className="text-dim">4 из 5</span>
                  </span>
                  Верная база. Уточните TTL ключей и поведение при конкурентных
                  повторах — в финтехе спросят.
                </ChatBubble>
              </ChatShell>

              <div>
                <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">
                  Интервью по конкретной вакансии
                </h2>
                <p className="text-muted mt-4 max-w-[52ch] text-base">
                  Вставьте ссылку на вакансию — тренажёр разберёт требования и
                  соберёт вопросы под стек, домен и уровень позиции. Вы
                  репетируете именно то собеседование, на которое идёте.
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
            </div>
          </Reveal>
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <SectionHead title="Что вы получаете после сессии">
              Не «правильные ответы», а честная картина вашей готовности.
            </SectionHead>
          </Reveal>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {results.map((r, i) => (
              <Reveal key={r.title} delay={i * 0.05}>
                <FeatureCard icon={r.icon} title={r.title}>
                  {r.body}
                </FeatureCard>
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section className="pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Первое интервью — бесплатно"
              actions={
                <>
                  <Link to={startTo} className={buttonClasses()}>
                    Начать интервью
                  </Link>
                  <Link
                    to="/skills-trainer"
                    className={buttonClasses({ variant: 'secondary' })}
                  >
                    А для отдельных тем — тренажёр
                  </Link>
                </>
              }
            >
              3 тренировки и пробное AI-интервью бесплатно. Карта не нужна.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
