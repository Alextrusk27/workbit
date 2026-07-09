import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Stars } from '@/components/ui/Stars'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { cn } from '@/lib/cn'
import type {
  SessionResponse,
  SessionSource,
  SessionStatus,
} from '@/features/interview/api'
import {
  sessionHeadline,
  sessionSubtitle,
  SOURCE_LABELS,
  STATUS_LABELS,
} from '@/features/interview/labels'
import {
  useDeleteSession,
  useReport,
  useSessions,
} from '@/features/interview/useInterview'
import { getErrorMessage } from '@/lib/api'
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

export function DashboardPage() {
  usePageTitle('Личный кабинет')
  const { data: sessions, isLoading, isError, error } = useSessions()
  const [tab, setTab] = useState<SessionSource>('CATALOG')
  const [status, setStatus] = useState<StatusFilter>('ALL')

  const newHref = `/app/interview/new?mode=${tab === 'VACANCY' ? 'vacancy' : 'catalog'}`

  return (
    <Container className="py-12 sm:py-16">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
            Личный кабинет
          </p>
          <h1 className="text-ink mt-4 text-3xl sm:text-4xl">Мои интервью</h1>
        </div>
        <Link to={newHref} className={buttonClasses()}>
          Новое интервью
        </Link>
      </div>

      <div className="mt-10">
        {isLoading && <SessionListSkeleton />}

        {isError && <Alert>{getErrorMessage(error)}</Alert>}

        {sessions && sessions.length === 0 && <EmptyState />}

        {sessions && sessions.length > 0 && (
          <SessionBrowser
            sessions={sessions}
            tab={tab}
            onTab={setTab}
            status={status}
            onStatus={setStatus}
          />
        )}
      </div>
    </Container>
  )
}

function SessionBrowser({
  sessions,
  tab,
  onTab,
  status,
  onStatus,
}: {
  sessions: SessionResponse[]
  tab: SessionSource
  onTab: (t: SessionSource) => void
  status: StatusFilter
  onStatus: (s: StatusFilter) => void
}) {
  const bySource: Record<SessionSource, SessionResponse[]> = {
    CATALOG: sessions.filter((s) => s.source === 'CATALOG'),
    VACANCY: sessions.filter((s) => s.source === 'VACANCY'),
  }
  const inTab = bySource[tab]
  const shown =
    status === 'ALL' ? inTab : inTab.filter((s) => s.status === status)

  return (
    <div>
      <div
        role="tablist"
        className="border-rule flex gap-1 border-b"
        aria-label="Разделы интервью"
      >
        {(['CATALOG', 'VACANCY'] as const).map((source) => {
          const selected = source === tab
          return (
            <button
              key={source}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => onTab(source)}
              className={cn(
                '-mb-px border-b-2 px-4 py-2.5 text-sm transition-colors',
                'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
                selected
                  ? 'border-accent text-ink'
                  : 'text-muted hover:text-ink border-transparent',
              )}
            >
              {SOURCE_LABELS[source]}
              <span className="text-muted ml-1.5 tabular-nums">
                {bySource[source].length}
              </span>
            </button>
          )
        })}
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((f) => {
          const count =
            f.key === 'ALL'
              ? inTab.length
              : inTab.filter((s) => s.status === f.key).length
          const selected = f.key === status
          return (
            <button
              key={f.key}
              type="button"
              aria-pressed={selected}
              onClick={() => onStatus(f.key)}
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
        {inTab.length === 0 ? (
          <p className="text-muted py-8 text-center text-sm">
            {tab === 'CATALOG'
              ? 'Пока нет тренировок по каталогу.'
              : 'Пока нет интервью по вакансиям.'}
          </p>
        ) : shown.length === 0 ? (
          <p className="text-muted py-8 text-center text-sm">
            Нет интервью с этим статусом.
          </p>
        ) : (
          <ul className="space-y-4">
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
        Запустите первую тренировку или интервью по вакансии — подберём вопросы,
        а рецензент разберёт ваши ответы.
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

function CardScore({ sessionId }: { sessionId: string }) {
  const { data, isLoading } = useReport(sessionId)
  if (isLoading) return <Skeleton className="mt-2 h-4 w-28" />
  if (!data) return null
  return (
    <div className="mt-2 flex items-center gap-2">
      <span className="text-accent text-base">
        <Stars value={Math.round(data.avgScore * 2) / 2} />
      </span>
      <span className="text-muted font-mono text-xs tabular-nums">
        {data.avgScore.toFixed(1).replace('.', ',')} из 5
      </span>
    </div>
  )
}

function SessionCard({
  session,
  index,
}: {
  session: SessionResponse
  index: number
}) {
  const del = useDeleteSession()
  const completed = session.status === 'COMPLETED'

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
            {sessionHeadline(session)}
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
            </span>
            <span>{formatDate(session.created)}</span>
          </div>
          {completed ? (
            <CardScore sessionId={session.id} />
          ) : (
            <p className="text-muted mt-2 text-xs italic">
              Завершите интервью и узнайте оценку
            </p>
          )}
        </div>

        <div className="flex items-center gap-3">
          {completed ? (
            <Link
              to={`/app/interview/${session.id}/report`}
              className={buttonClasses({ variant: 'secondary' })}
            >
              Отчёт
            </Link>
          ) : (
            <Link
              to={`/app/interview/${session.id}`}
              className={buttonClasses()}
            >
              Продолжить
            </Link>
          )}
          <button
            type="button"
            onClick={onDelete}
            disabled={del.isPending}
            className="text-muted hover:text-ink text-sm transition-colors disabled:opacity-50"
          >
            Удалить
          </button>
        </div>
      </div>
    </li>
  )
}
