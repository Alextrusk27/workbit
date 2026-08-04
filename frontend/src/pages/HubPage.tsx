import { Link } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

function SectionCard({
  to,
  eyebrow,
  title,
  description,
}: {
  to: string
  eyebrow: string
  title: string
  description: string
}) {
  return (
    <Link
      to={to}
      className="border-line bg-card hover:border-line-hover focus-visible:outline-indigo block rounded-2xl border px-7.5 py-8 transition hover:-translate-y-[3px] focus-visible:outline-2 focus-visible:outline-offset-2"
    >
      <p className="text-indigo text-xs font-semibold tracking-[0.14em] uppercase">
        {eyebrow}
      </p>
      <h2 className="text-ink mt-3.5 text-[22px] font-bold tracking-[-0.015em]">
        {title}
      </h2>
      <p className="text-muted mt-3 text-[14.5px]">{description}</p>
    </Link>
  )
}

export function HubPage() {
  usePageTitle('Личный кабинет')

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/', label: 'На главную' }}
        eyebrow="Личный кабинет"
        title="С чего начнём?"
      >
        Тренажёр прокачивает один навык под вашу профессию и уровень. Интервью
        готовит к конкретной вакансии с hh.ru и оценивает шансы на оффер.
      </AppPageHeader>

      <div className="mt-10 grid gap-5 sm:grid-cols-2">
        <SectionCard
          to="/app/training"
          eyebrow="Тренажёр"
          title="Тренировка навыка"
          description="Один навык за сессию: вопросы под навык, профессию и уровень сложности. Отвечайте по одному, разбор с оценками придёт в конце."
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
