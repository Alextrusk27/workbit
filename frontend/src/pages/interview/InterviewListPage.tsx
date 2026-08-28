import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { QuotaBadge } from '@/components/app/QuotaBadge'
import { Alert } from '@/components/ui/Alert'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Stars } from '@/components/ui/Stars'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { InterviewVacancy } from '@/features/interview/api'
import {
  OFFER_TONE,
  STATUS_LABELS,
  timesWord,
  VACANCY_STATUS_LABELS,
} from '@/features/interview/labels'
import { useInterviewVacancies } from '@/features/interview/useInterview'
import { useVacancyStatus } from '@/features/vacancy/useVacancy'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'

type StatusFilter = 'ALL' | 'PENDING' | 'COMPLETED'

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: 'Все' },
  { key: 'PENDING', label: STATUS_LABELS.IN_PROGRESS },
  { key: 'COMPLETED', label: STATUS_LABELS.COMPLETED },
]

function matches(vacancy: InterviewVacancy, filter: StatusFilter): boolean {
  if (filter === 'ALL') return true
  const completed = vacancy.status === 'COMPLETED'
  return filter === 'COMPLETED' ? completed : !completed
}

const OFFER_INLINE_CLASS = {
  low: 'text-ink',
  mid: 'text-indigo',
  high: 'text-ok',
} as const

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

function formatScore(score: number): string {
  return score.toFixed(1).replace('.', ',')
}

export function InterviewListPage() {
  usePageTitle('Интервью')
  const { data: vacancies, isLoading, isError, error } = useInterviewVacancies()

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/app', label: 'Рабочий стол' }}
        eyebrow="Интервью"
        title="Мои интервью"
        actions={
          <div className="flex items-center gap-4">
            <QuotaBadge kind="interview" />
            <Link to="/app/interview/new" className={buttonClasses()}>
              Новое интервью
            </Link>
          </div>
        }
      />

      <div className="mt-8">
        {isLoading && <VacancyListSkeleton />}

        {isError && <Alert>{getErrorMessage(error)}</Alert>}

        {vacancies && vacancies.length === 0 && <EmptyState />}

        {vacancies && vacancies.length > 0 && (
          <VacancyBrowser vacancies={vacancies} />
        )}
      </div>
    </Container>
  )
}

function VacancyBrowser({ vacancies }: { vacancies: InterviewVacancy[] }) {
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const shown = vacancies.filter((v) => matches(v, status))

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => (
          <Chip
            key={f.key}
            selected={f.key === status}
            onClick={() => setStatus(f.key)}
            count={vacancies.filter((v) => matches(v, f.key)).length}
          >
            {f.label}
          </Chip>
        ))}
      </div>

      <div className="mt-6">
        {shown.length === 0 ? (
          <p className="text-muted py-8 text-center text-sm">
            Нет интервью с этим статусом.
          </p>
        ) : (
          <ul key={status} className="flex flex-col gap-4">
            {shown.map((v) => (
              <VacancyCard key={v.vacancyId} vacancy={v} />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function VacancyListSkeleton() {
  return (
    <div role="status" className="flex flex-col gap-4">
      <span className="sr-only">Загрузка списка интервью…</span>
      {[0, 1, 2].map((i) => (
        <div key={i} className="border-line bg-card rounded-xl border p-6">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="mt-3 h-4 w-28" />
          <Skeleton className="mt-4 h-4 w-56" />
        </div>
      ))}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="border-line rounded-xl border border-dashed p-10 text-center">
      <h2 className="text-ink text-xl font-bold">Пока нет интервью</h2>
      <p className="text-muted mx-auto mt-2 max-w-md text-sm">
        Вставь ссылку на вакансию с hh.ru — рецензент подберёт вопросы под неё,
        а в конце разберёт ответы и оценит шансы на оффер.
      </p>
      <Link
        to="/app/interview/new"
        className={cn('mt-6', buttonClasses({ size: 'lg' }))}
      >
        Начать первое интервью
      </Link>
    </div>
  )
}

function VacancyLine({ vacancy }: { vacancy: InterviewVacancy }) {
  const { data } = useVacancyStatus(vacancy.vacancyUrl)
  if (!vacancy.vacancyUrl) return null
  return (
    <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[12.5px]">
      <a
        href={vacancy.vacancyUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="text-indigo hover:text-violet transition-colors"
      >
        Вакансия на hh.ru ↗
      </a>
      {data && (
        <span
          className={cn(
            'font-semibold',
            data.status === 'ACTIVE' ? 'text-ok' : 'text-danger',
          )}
        >
          {VACANCY_STATUS_LABELS[data.status]}
        </span>
      )}
      {vacancy.experience && <span>Опыт: {vacancy.experience}</span>}
    </div>
  )
}

function VacancyCard({ vacancy }: { vacancy: InterviewVacancy }) {
  const navigate = useNavigate()
  const to = `/app/interview/vacancy/${vacancy.vacancyId}`

  return (
    <li
      className="border-line bg-card hover:border-line-hover cursor-pointer rounded-xl border px-6 py-5.5 transition-colors"
      onClick={(e) => {
        if (!(e.target as HTMLElement).closest('a, button')) navigate(to)
      }}
    >
      <div className="min-w-0">
        <h2 className="text-ink text-[17px] font-semibold tracking-[-0.01em] break-words">
          <Link
            to={to}
            className="hover:text-indigo focus-visible:outline-indigo rounded-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            {vacancy.vacancyName}
          </Link>
        </h2>
        <p className="text-muted mt-1 text-[13.5px] break-words">
          {vacancy.employer || 'Работодатель не указан'}
        </p>
        <div className="text-dim mt-3 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 text-[12.5px]">
          {vacancy.bestScore != null && (
            <span className="flex items-center gap-2">
              Лучший результат:{' '}
              <Stars value={Math.round(vacancy.bestScore * 2) / 2} />
              <span className="text-ink font-semibold tabular-nums">
                {formatScore(vacancy.bestScore)}
              </span>
            </span>
          )}
          {vacancy.bestOffer && (
            <span>
              Оффер:{' '}
              <span
                className={cn(
                  'font-semibold',
                  OFFER_INLINE_CLASS[OFFER_TONE[vacancy.bestOffer]],
                )}
              >
                {vacancy.bestOffer}
              </span>
            </span>
          )}
          {vacancy.completedCount > 0 && (
            <span>
              Пройдено: {vacancy.completedCount}{' '}
              {timesWord(vacancy.completedCount)}
            </span>
          )}
          <span>{formatDate(vacancy.lastActivity)}</span>
        </div>
        <VacancyLine vacancy={vacancy} />
        {vacancy.bestScore == null && (
          <p className="text-dim mt-2 text-[12.5px] italic">
            Заверши интервью и узнай оценку и шансы на оффер
          </p>
        )}
      </div>
    </li>
  )
}
