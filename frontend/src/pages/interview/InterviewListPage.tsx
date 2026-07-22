import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
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
    <Container className="py-12 sm:py-16">
      <Link
        to="/app"
        className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
      >
        ← Личный кабинет
      </Link>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
            Интервью
          </p>
          <h1 className="text-ink mt-4 text-3xl sm:text-4xl">Мои интервью</h1>
        </div>
        <Link to="/app/interview/new" className={buttonClasses()}>
          Новое интервью
        </Link>
      </div>

      <div className="mt-10">
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
        {STATUS_FILTERS.map((f) => {
          const count =
            f.key === 'ALL'
              ? sessions.length
              : sessions.filter((s) => s.status === f.key).length
          const selected = f.key === status
          return (
            <button
              key={f.key}
              type="button"
              aria-pressed={selected}
              onClick={() => setStatus(f.key)}
              className={cn(
                'rounded-full border px-3 py-1 text-xs transition-colors',
                'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
                selected
                  ? 'border-accent bg-accent text-paper'
                  : 'border-rule text-muted hover:border-ink/30 hover:text-ink',
              )}
            >
              {f.label}
              <span className="ml-1 tabular-nums opacity-70">{count}</span>
            </button>
          )
        })}
      </div>

      <div className="mt-6">
        {shown.length === 0 ? (
          <p className="text-muted py-8 text-center text-sm">
            Нет интервью с этим статусом.
          </p>
        ) : (
          <ul key={status} className="space-y-4">
            {shown.map((s, i) => (
              <SessionCard key={s.id} session={s} index={i} />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

function SessionListSkeleton() {
  return (
    <div role="status" className="space-y-4">
      <span className="sr-only">Загрузка списка интервью…</span>
      {[0, 1, 2].map((i) => (
        <div key={i} className="border-rule rounded-lg border p-5">
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
    <div className="border-rule rounded-lg border border-dashed p-10 text-center">
      <h2 className="text-ink font-display text-xl">Пока нет интервью</h2>
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

function CardResult({ sessionId }: { sessionId: string }) {
  const { data, isLoading } = useInterviewReport(sessionId)
  if (isLoading) return <Skeleton className="mt-2 h-4 w-40" />
  if (!data) return null
  return (
    <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-2">
      {data.avgScore != null && (
        <span className="flex items-center gap-2">
          <span className="text-accent text-base">
            <Stars value={Math.round(data.avgScore * 2) / 2} />
          </span>
          <span className="text-muted font-mono text-xs tabular-nums">
            {data.avgScore.toFixed(1).replace('.', ',')} из 5
          </span>
        </span>
      )}
      <span className="text-muted text-xs">
        Оффер: <OfferBadgeInline value={data.offerProbability} />
      </span>
    </div>
  )
}

function OfferBadgeInline({ value }: { value: string }) {
  return <span className="text-ink">{value}</span>
}

function SessionCard({
  session,
  index,
}: {
  session: InterviewSession
  index: number
}) {
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
    <li
      className="border-rule hover:border-ink/20 animate-rise rounded-lg border p-5 transition-colors"
      style={{ animationDelay: `${Math.min(index, 6) * 50}ms` }}
    >
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0">
          <h2 className="text-ink font-display text-lg break-words">
            <Link
              to={openHref}
              className="hover:text-accent focus-visible:outline-accent rounded-sm transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              {sessionHeadline(session)}
            </Link>
          </h2>
          <p className="text-muted mt-1 text-sm break-words">
            {sessionSubtitle(session)}
          </p>
          <div className="text-muted mt-3 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
            <span
              className={cn(
                'rounded-sm px-2 py-0.5 font-mono',
                completed ? 'bg-pine/10 text-pine' : 'bg-accent/10 text-accent',
              )}
            >
              {STATUS_LABELS[session.status]}
            </span>
            <span className="tabular-nums">
              {session.answeredCount} / {session.totalQuestions} вопросов
              отвечено
            </span>
            <span>{formatDate(session.created)}</span>
          </div>
          {completed ? (
            <CardResult sessionId={session.id} />
          ) : (
            <p className="text-muted mt-2 text-xs italic">
              Завершите интервью и узнайте оценку и шансы на оффер
            </p>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <Link
            to={openHref}
            className={buttonClasses({ variant: 'secondary' })}
          >
            {completed ? 'Разбор' : 'Продолжить'}
          </Link>
          <button
            type="button"
            onClick={onDelete}
            disabled={del.isPending}
            className="text-muted hover:text-ink self-center text-sm transition-colors disabled:opacity-50"
          >
            Удалить
          </button>
        </div>
      </div>
    </li>
  )
}
