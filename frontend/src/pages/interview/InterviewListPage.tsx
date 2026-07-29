import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { StatusTag } from '@/components/app/StatusTag'
import { Alert } from '@/components/ui/Alert'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Stars } from '@/components/ui/Stars'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { InterviewSession, SessionStatus } from '@/features/interview/api'
import {
  sessionHeadline,
  sessionSubtitle,
  STATUS_LABELS,
} from '@/features/interview/labels'
import {
  useDeleteInterview,
  useInterviewReport,
  useInterviewSessions,
} from '@/features/interview/useInterview'
import type { VacancyStatus } from '@/features/vacancy/api'
import { useVacancyStatus } from '@/features/vacancy/useVacancy'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'

type StatusFilter = 'ALL' | SessionStatus

const STATUS_FILTERS: { key: StatusFilter; label: string }[] = [
  { key: 'ALL', label: 'Все' },
  { key: 'CREATED', label: STATUS_LABELS.CREATED },
  { key: 'IN_PROGRESS', label: STATUS_LABELS.IN_PROGRESS },
  { key: 'COMPLETED', label: STATUS_LABELS.COMPLETED },
]

const VACANCY_STATUS_LABELS: Record<VacancyStatus, string> = {
  ACTIVE: 'активна',
  ARCHIVED: 'в архиве',
  NOT_FOUND: 'удалена',
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

export function InterviewListPage() {
  usePageTitle('Интервью')
  const { data: sessions, isLoading, isError, error } = useInterviewSessions()

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/app', label: 'Личный кабинет' }}
        eyebrow="Интервью"
        title="Мои интервью"
        actions={
          <Link to="/app/interview/new" className={buttonClasses()}>
            Новое интервью
          </Link>
        }
      />

      <div className="mt-8">
        {isLoading && <SessionListSkeleton />}

        {isError && <Alert>{getErrorMessage(error)}</Alert>}

        {sessions && sessions.length === 0 && <EmptyState />}

        {sessions && sessions.length > 0 && (
          <SessionBrowser sessions={sessions} />
        )}
      </div>
    </Container>
  )
}

function SessionBrowser({ sessions }: { sessions: InterviewSession[] }) {
  const [status, setStatus] = useState<StatusFilter>('ALL')
  const shown =
    status === 'ALL' ? sessions : sessions.filter((s) => s.status === status)

  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => (
          <Chip
            key={f.key}
            selected={f.key === status}
            onClick={() => setStatus(f.key)}
            count={
              f.key === 'ALL'
                ? sessions.length
                : sessions.filter((s) => s.status === f.key).length
            }
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
            {shown.map((s) => (
              <SessionCard key={s.id} session={s} />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function SessionListSkeleton() {
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
        Вставьте ссылку на вакансию с hh.ru — рецензент подберёт вопросы под
        неё, а в конце разберёт ответы и оценит шансы на оффер.
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

function VacancyLine({ session }: { session: InterviewSession }) {
  const { data } = useVacancyStatus(session.vacancyUrl)
  if (!session.vacancyUrl) return null
  return (
    <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-1 text-[12.5px]">
      <a
        href={session.vacancyUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="text-indigo hover:text-violet transition-colors"
      >
        Вакансия на hh.ru ↗
      </a>
      {data && <span>{VACANCY_STATUS_LABELS[data.status]}</span>}
      {session.experience && <span>Опыт: {session.experience}</span>}
    </div>
  )
}

function CardResult({ sessionId }: { sessionId: string }) {
  const { data, isLoading } = useInterviewReport(sessionId)
  if (isLoading) return <Skeleton className="mt-2 h-4 w-40" />
  if (!data) return null
  return (
    <div className="text-dim mt-2 flex flex-wrap items-center gap-x-3.5 gap-y-2 text-[12.5px]">
      {data.avgScore != null && (
        <span className="flex items-center gap-2">
          <Stars value={Math.round(data.avgScore * 2) / 2} />
          <span className="tabular-nums">
            {data.avgScore.toFixed(1).replace('.', ',')} из 5
          </span>
        </span>
      )}
      <span>
        Оффер: <span className="text-ink">{data.offerProbability}</span>
      </span>
    </div>
  )
}

function SessionCard({ session }: { session: InterviewSession }) {
  const del = useDeleteInterview()
  const completed = session.status === 'COMPLETED'
  const openHref = completed
    ? `/app/interview/${session.id}/report`
    : `/app/interview/${session.id}`

  const onDelete = () => {
    if (!window.confirm('Удалить это интервью? Действие необратимо.')) return
    del.mutate(session.id)
  }

  return (
    <li className="border-line bg-card hover:border-line-hover flex flex-wrap justify-between gap-5 rounded-xl border px-6 py-5.5 transition-colors">
      <div className="min-w-0">
        <h2 className="text-ink text-[17px] font-semibold tracking-[-0.01em] break-words">
          <Link
            to={openHref}
            className="hover:text-indigo focus-visible:outline-indigo rounded-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
          >
            {sessionHeadline(session)}
          </Link>
        </h2>
        <p className="text-muted mt-1 text-[13.5px] break-words">
          {sessionSubtitle(session)}
        </p>
        <div className="text-dim mt-3 flex flex-wrap items-center gap-x-3.5 gap-y-1.5 text-[12.5px]">
          <StatusTag label={STATUS_LABELS[session.status]} done={completed} />
          <span className="tabular-nums">
            {session.answeredCount} / {session.totalQuestions} вопросов отвечено
          </span>
          <span>{formatDate(session.created)}</span>
        </div>
        <VacancyLine session={session} />
        {completed ? (
          <CardResult sessionId={session.id} />
        ) : (
          <p className="text-dim mt-2 text-[12.5px] italic">
            Завершите интервью и узнайте оценку и шансы на оффер
          </p>
        )}
      </div>

      <div className="flex flex-col items-center justify-center gap-2.5">
        <Link
          to={openHref}
          className={buttonClasses({ variant: 'secondary', size: 'sm' })}
        >
          {completed ? 'Разбор' : 'Продолжить'}
        </Link>
        <button
          type="button"
          onClick={onDelete}
          disabled={del.isPending}
          className="text-dim hover:text-ink text-[13px] transition-colors disabled:opacity-50"
        >
          Удалить
        </button>
      </div>
    </li>
  )
}
