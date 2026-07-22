import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'

function SectionCard({
  to,
  eyebrow,
  title,
  description,
  badge,
}: {
  to: string
  eyebrow: string
  title: string
  description: string
  badge?: string
}) {
  return (
    <Link
      to={to}
      className={cn(
        'border-rule hover:border-ink/25 group animate-rise block rounded-lg border p-6 transition-colors sm:p-8',
        'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
      )}
    >
      <div className="flex items-center justify-between">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          {eyebrow}
        </p>
        {badge && (
          <span className="text-muted border-rule rounded-full border px-2 py-0.5 font-mono text-xs">
            {badge}
          </span>
        )}
      </div>
      <h2 className="text-ink group-hover:text-accent font-display mt-4 text-2xl transition-colors">
        {title}
      </h2>
      <p className="text-muted mt-3 text-sm leading-relaxed">{description}</p>
    </Link>
  )
}

export function HubPage() {
  usePageTitle('Личный кабинет')

  return (
    <Container className="py-12 sm:py-16">
      <Link
        to="/"
        className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
      >
        ← На главную
      </Link>
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Личный кабинет
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">С чего начнём?</h1>
      <p className="text-muted mt-4 max-w-xl">
        Тренажёр отрабатывает вопросы под профессию и уровень. Интервью готовит
        к конкретной вакансии с hh.ru и оценивает шансы на оффер.
      </p>

      <div className="mt-10 grid gap-5 sm:grid-cols-2">
        <SectionCard
          to="/app/training"
          eyebrow="Тренажёр"
          title="Тренировка собеседования"
          description="Вопросы под профессию, уровень и тип компании. Отвечайте по одному, разбор с оценками придёт в конце."
        />
        <SectionCard
          to="/app/interview"
          eyebrow="Интервью"
          title="Интервью под вакансию"
          description="Собеседование под конкретную вакансию с hh.ru. Вопросы по её требованиям, а в конце — разбор и вероятность оффера."
        />
      </div>
    </Container>
  )
}
