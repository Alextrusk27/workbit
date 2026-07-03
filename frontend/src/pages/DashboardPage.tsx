import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { cn } from '@/lib/cn'
import type { SessionResponse } from '@/features/interview/api'
import { STATUS_LABELS } from '@/features/interview/labels'
import {
  useDeleteSession,
  useSessions,
} from '@/features/interview/useInterview'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

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

  return (
    <Container className="py-12 sm:py-16">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
            Личный кабинет
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
          <ul className="space-y-4">
            {sessions.map((s, i) => (
              <SessionCard key={s.id} session={s} index={i} />
            ))}
          </ul>
        )}
      </div>
    </Container>
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
        Запустите первую тренировку — подберём вопросы под профессию, уровень и
        тип компании, а рецензент разберёт ваши ответы.
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
        <div>
          <h2 className="text-ink font-display text-lg">
            {session.profession}
          </h2>
          <p className="text-muted mt-1 text-sm">
            {session.level} · {session.companyType}
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
            <span>
              {session.answeredCount} / {session.totalQuestions} вопросов
            </span>
            <span>{formatDate(session.created)}</span>
          </div>
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
