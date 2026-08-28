import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Skeleton } from '@/components/ui/Skeleton'
import { useAuth, useDeleteAccount } from '@/features/auth/useAuth'
import type {
  UsageCounter,
  UsageEvent,
  UsageEventKind,
  UsageTarget,
} from '@/features/billing/api'
import { PLAN_LABELS } from '@/features/billing/labels'
import { useQuota, useUsage } from '@/features/billing/useBilling'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { trainingsWord } from '@/lib/plural'
import { usePageTitle } from '@/lib/usePageTitle'

const DELETE_WARNING =
  'Аккаунт и вся история интервью и тренировок удаляются безвозвратно. Все ' +
  'неиспользованные лимиты тарифа сгорают без возврата. Восстановить их ' +
  'будет нельзя.'

function formatExpiry(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })
}

export function SettingsPage() {
  usePageTitle('Аккаунт')
  const { user } = useAuth()
  const { data: quota } = useQuota()

  return (
    <Container className="max-w-160">
      <AppPageHeader title="Аккаунт">
        {user && (
          <span className="flex flex-wrap items-center gap-2.5">
            <span>
              Ты вошёл как <span className="text-ink">{user.email}</span>
            </span>
            {quota && (
              <span className="border-indigo/40 bg-indigo/12 text-indigo inline-flex rounded-full border px-3 py-0.5 text-[12.5px] font-semibold">
                {PLAN_LABELS[quota.plan]}
                {quota.planExpiresAt &&
                  ` · до ${formatExpiry(quota.planExpiresAt)}`}
              </span>
            )}
          </span>
        )}
      </AppPageHeader>

      <div className="mt-10">
        <PlanSection />
      </div>

      <div className="mt-10">
        <DeleteAccountSection />
      </div>
    </Container>
  )
}

function PlanSection() {
  const { data: quota, isLoading } = useQuota()
  const usage = useUsage()

  return (
    <section>
      <h2 className="text-ink text-[21px] font-bold">Тариф</h2>

      {isLoading && (
        <div role="status" className="mt-4">
          <span className="sr-only">Загрузка тарифа…</span>
          <Skeleton className="h-5 w-40" />
          <Skeleton className="mt-3 h-4 w-64" />
        </div>
      )}

      {quota && (
        <p className="text-ink mt-4 font-semibold">
          {PLAN_LABELS[quota.plan]}
          {quota.planExpiresAt && (
            <span className="text-muted font-normal">
              {' '}
              · действует до {formatExpiry(quota.planExpiresAt)}
            </span>
          )}
        </p>
      )}

      {usage.data && (
        <>
          <div className="mt-5 grid gap-4 sm:grid-cols-2">
            <LimitCard title="AI-интервью" counters={usage.data.interviews} />
            <LimitCard title="Тренировки" counters={usage.data.trainings} />
          </div>
          <UsageHistory events={usage.data.events} />
        </>
      )}

      {usage.isError && (
        <p className="text-dim mt-4 text-sm">
          Остатки и история операций временно недоступны.
        </p>
      )}

      <p className="text-dim mt-5 text-sm">
        Сменить или продлить тариф можно на странице{' '}
        <Link
          to="/pricing"
          className="text-indigo hover:text-violet transition-colors"
        >
          тарифов
        </Link>
        .
      </p>
    </section>
  )
}

function LimitCard({
  title,
  counters,
}: {
  title: string
  counters: UsageCounter
}) {
  const { left, total } = counters

  if (left === null || total === null) {
    return (
      <div className="border-line bg-card rounded-xl border p-5">
        <Eyebrow>{title}</Eyebrow>
        <p className="mt-2">
          <span className="text-ink text-[28px] font-extrabold tracking-[-0.02em]">
            Безлимит
          </span>
        </p>
        <p className="text-dim mt-2.5 text-[12.5px]">
          Без ограничений на твоём тарифе
        </p>
      </div>
    )
  }

  const used = total - left
  const usedShare = total > 0 ? (used / total) * 100 : 0

  return (
    <div className="border-line bg-card rounded-xl border p-5">
      <Eyebrow>{title}</Eyebrow>
      <p className="mt-2">
        <span className="text-ink text-[28px] font-extrabold tracking-[-0.02em] tabular-nums">
          {left}
        </span>
        <span className="text-muted text-sm"> доступно из {total}</span>
      </p>
      <div className="bg-line mt-3.5 h-1.5 overflow-hidden rounded-full">
        <div
          className="bg-grad h-full rounded-full"
          style={{ width: `${usedShare}%` }}
        />
      </div>
      <p className="text-dim mt-2.5 text-[12.5px] tabular-nums">
        Использовано {used} из {total}
      </p>
    </div>
  )
}

interface HistoryRow {
  at: string
  kind: UsageEventKind
  label: string
  deltas: { target: UsageTarget; delta: number }[]
}

function toRows(events: UsageEvent[]): HistoryRow[] {
  const rows: HistoryRow[] = []
  for (const event of events) {
    const last = rows[rows.length - 1]
    if (last && last.at === event.at && last.label === event.label) {
      last.deltas.push({ target: event.target, delta: event.delta })
    } else {
      rows.push({
        at: event.at,
        kind: event.kind,
        label: event.label,
        deltas: [{ target: event.target, delta: event.delta }],
      })
    }
  }
  return rows
}

function deltaText(row: HistoryRow): string {
  const sign = row.kind === 'SPEND' ? '−' : '+'
  return row.deltas
    .map(({ target, delta }) => {
      const unit = target === 'INTERVIEW' ? 'интервью' : trainingsWord(delta)
      return `${sign}${Math.abs(delta)} ${unit}`
    })
    .join(', ')
}

function formatEventDate(iso: string): string {
  const date = new Date(iso)
  const day = date.toLocaleDateString('ru-RU', {
    day: 'numeric',
    month: 'long',
  })
  const time = date.toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  })
  return `${day}, ${time}`
}

const HISTORY_PREVIEW = 5

const KIND_FILTERS = [
  { value: 'ALL', label: 'Все' },
  { value: 'SPEND', label: 'Списания' },
  { value: 'CREDIT', label: 'Пополнения' },
] as const

type KindFilter = (typeof KIND_FILTERS)[number]['value']

function UsageHistory({ events }: { events: UsageEvent[] }) {
  const [filter, setFilter] = useState<KindFilter>('ALL')
  const [expanded, setExpanded] = useState(false)

  const rows = toRows(events)
  const filtered =
    filter === 'ALL' ? rows : rows.filter((r) => r.kind === filter)
  const visible = expanded ? filtered : filtered.slice(0, HISTORY_PREVIEW)

  const count = (value: KindFilter) =>
    value === 'ALL' ? rows.length : rows.filter((r) => r.kind === value).length

  return (
    <div className="mt-7">
      <h3 className="text-ink text-[15px] font-semibold">История операций</h3>

      {rows.length === 0 ? (
        <p className="text-dim mt-3 text-sm">Операций пока нет.</p>
      ) : (
        <>
          <div className="mt-3 flex flex-wrap gap-2">
            {KIND_FILTERS.map(({ value, label }) => (
              <Chip
                key={value}
                selected={filter === value}
                count={count(value)}
                onClick={() => setFilter(value)}
              >
                {label}
              </Chip>
            ))}
          </div>

          <ul className="mt-3">
            {visible.map((row) => (
              <li
                key={`${row.at}|${row.label}`}
                className="border-divider grid grid-cols-[1fr_auto] gap-x-4 gap-y-0.5 border-t py-[11px] last:border-b sm:flex sm:items-baseline sm:gap-4"
              >
                <span className="text-dim col-start-1 row-start-1 text-[13px] tabular-nums sm:w-[140px] sm:shrink-0">
                  {formatEventDate(row.at)}
                </span>
                <span className="text-ink col-span-2 col-start-1 row-start-2 text-sm break-words sm:col-span-1 sm:flex-1">
                  {row.label}
                </span>
                <span
                  className={cn(
                    'col-start-2 row-start-1 text-right text-[13px] tabular-nums sm:text-left sm:whitespace-nowrap',
                    row.kind === 'SPEND' ? 'text-muted' : 'text-ok',
                  )}
                >
                  {deltaText(row)}
                </span>
              </li>
            ))}
            {visible.length === 0 && (
              <li className="text-dim border-divider border-t py-[11px] text-sm last:border-b">
                Таких операций нет.
              </li>
            )}
          </ul>

          {!expanded && filtered.length > HISTORY_PREVIEW && (
            <button
              type="button"
              onClick={() => setExpanded(true)}
              className="text-dim hover:text-ink focus-visible:outline-indigo mt-3 rounded-sm text-[13px] underline underline-offset-4 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
            >
              Показать все операции
            </button>
          )}
        </>
      )}
    </div>
  )
}

function DeleteAccountSection() {
  const navigate = useNavigate()
  const del = useDeleteAccount()
  const [confirming, setConfirming] = useState(false)

  const onDelete = () => {
    setConfirming(false)
    del.mutate(undefined, {
      onSuccess: () => navigate('/', { replace: true }),
    })
  }

  return (
    <section>
      <h2 className="text-ink text-[21px] font-bold">Удаление аккаунта</h2>
      <p className="text-muted mt-2 max-w-[48ch] text-sm">{DELETE_WARNING}</p>

      {del.isError && (
        <div className="mt-5">
          <Alert>{getErrorMessage(del.error)}</Alert>
        </div>
      )}

      <Button
        variant="danger"
        onClick={() => setConfirming(true)}
        disabled={del.isPending}
        className="mt-6"
      >
        {del.isPending ? 'Удаляем…' : 'Удалить аккаунт'}
      </Button>

      <ConfirmDialog
        open={confirming}
        title="Удалить аккаунт?"
        text={DELETE_WARNING}
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </section>
  )
}
