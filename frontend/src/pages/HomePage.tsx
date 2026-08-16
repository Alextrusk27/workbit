import { useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { Stars } from '@/components/ui/Stars'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { Badge } from '@/components/marketing/Badge'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { FeatureCard } from '@/components/marketing/FeatureCard'
import { HeroChatDemo } from '@/components/marketing/HeroChatDemo'
import { Reveal } from '@/components/marketing/Reveal'
import { SectionHead } from '@/components/marketing/SectionHead'
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
import { usePageTitle } from '@/lib/usePageTitle'
import { cn } from '@/lib/cn'

const features = [
  {
    icon: <IconRole />,
    title: 'Вопросы под роль',
    body: 'Навык, профессия и уровень сложности — вопросы подстраиваются под то, к чему вы готовитесь.',
  },
  {
    icon: <IconLink />,
    title: 'Интервью по вакансии',
    body: 'Вставьте ссылку на вакансию с hh.ru — тренажёр соберёт сессию под конкретные требования работодателя.',
  },
  {
    icon: <IconStar />,
    title: 'Оценка каждого ответа',
    body: 'Звёзды от 1 до 5 за каждый ответ. В отчёте после сессии видно, где просели и что подтянуть.',
  },
  {
    icon: <IconPencil />,
    title: 'Правки на полях',
    body: 'Рецензент отмечает сильное и указывает, что уточнить — как редактор в вашем тексте.',
  },
  {
    icon: <IconChart />,
    title: 'Вероятность оффера',
    body: 'Итоговый фидбэк по интервью и оценка шансов — низкая, средняя или высокая.',
  },
  {
    icon: <IconClock />,
    title: 'История сессий',
    body: 'Все интервью сохраняются — возвращайтесь к разборам и следите за прогрессом от сессии к сессии.',
  },
]

const tabs = [
  { id: 'question', label: 'Вопрос' },
  { id: 'answer', label: 'Ответ' },
  { id: 'review', label: 'Разбор' },
]

export function HomePage() {
  usePageTitle()
  const { isAuthenticated } = useAuth()
  const [tab, setTab] = useState(0)
  const startTo = isAuthenticated ? '/app' : '/login'

  const onTabKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowRight') setTab((t) => (t + 1) % tabs.length)
    if (e.key === 'ArrowLeft')
      setTab((t) => (t - 1 + tabs.length) % tabs.length)
  }

  return (
    <>
      <header className="glow-hero relative overflow-hidden pt-24 pb-20">
        <Container className="relative grid items-center gap-16 lg:grid-cols-[minmax(0,6fr)_minmax(0,5fr)]">
          <div>
            <Badge>AI-рецензент оценивает каждый ответ</Badge>
            <h1 className="text-ink mt-6 text-[clamp(38px,5vw,58px)] leading-[1.08] font-extrabold tracking-[-0.03em]">
              Тренажёр собеседований{' '}
              <span className="text-grad">с AI-интервьюером</span>
            </h1>
            <p className="text-muted mt-5.5 max-w-[46ch] text-lg">
              Реалистичные вопросы под вашу профессию и уровень, разбор каждого
              ответа и вероятность оффера. Готовьтесь до собеседования, а не на
              нём.
            </p>
            <div className="mt-8 flex flex-wrap gap-3.5">
              <Link to={startTo} className={buttonClasses()}>
                Начать интервью — бесплатно
              </Link>
              <Link
                to="/#demo"
                className={buttonClasses({ variant: 'secondary' })}
              >
                Посмотреть демо
              </Link>
            </div>
            <p className="text-dim mt-4.5 text-[13.5px]">
              1 интервью и 3 тренировки — бесплатно
            </p>
          </div>

          <HeroChatDemo />
        </Container>
      </header>

      <section className="py-16">
        <Container>
          <Reveal>
            <SectionHead title="Всё как на настоящем интервью">
              От выбора роли до финального вердикта — полный цикл подготовки в
              одном тренажёре.
            </SectionHead>
          </Reveal>
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {features.map((f, i) => (
              <Reveal key={f.title} delay={i * 0.05}>
                <FeatureCard icon={f.icon} title={f.title}>
                  {f.body}
                </FeatureCard>
              </Reveal>
            ))}
          </div>
        </Container>
      </section>

      <section id="demo" className="scroll-mt-20 py-16">
        <Container>
          <Reveal>
            <SectionHead title="Как проходит сессия">
              Три шага — от вопроса до разбора.
            </SectionHead>
          </Reveal>

          <Reveal className="mx-auto max-w-200">
            <div
              role="tablist"
              aria-label="Этапы сессии"
              onKeyDown={onTabKey}
              className="mb-7 flex justify-center gap-2"
            >
              {tabs.map((t, i) => (
                <button
                  key={t.id}
                  type="button"
                  role="tab"
                  id={`tab-${t.id}`}
                  aria-selected={tab === i}
                  aria-controls={`panel-${t.id}`}
                  tabIndex={tab === i ? 0 : -1}
                  onClick={() => setTab(i)}
                  className={cn(
                    'rounded-full border px-5 py-2.5 text-sm font-semibold transition',
                    tab === i
                      ? 'border-indigo/35 bg-indigo/12 text-ink'
                      : 'text-muted hover:text-ink border-transparent',
                  )}
                >
                  {t.label}
                </button>
              ))}
            </div>

            <div className="border-line bg-card rounded-2xl border p-7">
              <div
                role="tabpanel"
                id="panel-question"
                aria-labelledby="tab-question"
                hidden={tab !== 0}
                className="flex flex-col gap-3.5"
              >
                <ChatBubble
                  role="bot"
                  who="Workbit-интервьюер · вопрос 5 / 10"
                  className="max-w-full"
                >
                  Расскажите о случае, когда вы не уложились в срок. Что пошло
                  не так и что вы изменили после?
                </ChatBubble>
                <p className="text-dim text-[13.5px]">
                  Вопросы приходят по одному, без вариантов ответа — как в
                  реальном разговоре.
                </p>
              </div>

              <div
                role="tabpanel"
                id="panel-answer"
                aria-labelledby="tab-answer"
                hidden={tab !== 1}
                className="flex flex-col gap-3.5"
              >
                <ChatBubble role="user" who="Вы" className="max-w-full">
                  На прошлом проекте мы недооценили интеграцию с платёжным
                  провайдером: тестовый контур вёл себя не как боевой. Я вынес
                  интеграционные риски в отдельный спайк и с тех пор закладываю
                  их до оценки сроков.
                </ChatBubble>
                <p className="text-dim text-[13.5px]">
                  Отвечаете своими словами — текстом или голосом. Тренажёр ждёт
                  столько, сколько нужно.
                </p>
              </div>

              <div
                role="tabpanel"
                id="panel-review"
                aria-labelledby="tab-review"
                hidden={tab !== 2}
                className="flex flex-col gap-3.5"
              >
                <ChatBubble
                  role="bot"
                  who="Разбор рецензента"
                  className="max-w-full"
                >
                  <span className="mb-1.5 flex items-center gap-2 text-[13px]">
                    <Stars value={4} />
                    <span className="text-dim">4 из 5</span>
                  </span>
                  Хорошая структура: ситуация → причина → вывод. Добавьте
                  масштаб задержки и реакцию команды — интервьюеры почти всегда
                  спрашивают об этом следом.
                </ChatBubble>
                <p className="text-dim text-[13.5px]">
                  Разбор приходит в конце сессии — вместе с итоговым фидбэком и
                  вероятностью оффера.
                </p>
              </div>
            </div>
          </Reveal>
        </Container>
      </section>

      <section id="pricing" className="scroll-mt-20 py-16">
        <Container>
          <Reveal>
            <SectionHead title="Простые и честные тарифы">
              Начните бесплатно. Про — когда готовитесь всерьёз, Макс — когда
              сессий нужно больше.
              {promoActive &&
                ' До 1 октября к покупке — до 5 интервью в подарок.'}
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

      <section className="pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title="Следующее собеседование — уже не первое"
              actions={
                <>
                  <Link to={startTo} className={buttonClasses()}>
                    Начать интервью
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
              Проведите пробное интервью сегодня и приходите на настоящее
              подготовленным.
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
