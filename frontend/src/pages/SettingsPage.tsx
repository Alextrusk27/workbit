import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Limits } from '@/components/ui/LimitIcon'
import { Skeleton } from '@/components/ui/Skeleton'
import { OPERATION_COST } from '@/content/limits'
import { useAuth, useDeleteAccount } from '@/features/auth/useAuth'
import type { Balance, UsageEvent } from '@/features/billing/api'
import { useBalance, useUsage } from '@/features/billing/useBilling'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { formatDate, formatDay } from '@/lib/dates'
import { usePageTitle } from '@/lib/usePageTitle'

const DELETE_WARNING =
  'Аккаунт и вся история интервью и тренировок удаляются безвозвратно. Все ' +
  'неиспользованные лимиты сгорают без возврата. Восстановить их будет ' +
  'нельзя.'

export function SettingsPage() {
  usePageTitle('Аккаунт')
  const { user } = useAuth()
  const { data: balance } = useBalance()

  return (
    <Container className="max-w-160">
      <AppPageHeader title="Аккаунт">
        {user && (
          <span className="flex flex-wrap items-center gap-2.5">
            <span>
              Ты вошёл как <span className="text-ink">{user.email}</span>
            </span>
            {balance && (
              <span className="border-indigo/40 bg-indigo/12 text-indigo inline-flex rounded-full border px-3 py-0.5 text-[12.5px] font-semibold tabular-nums">
                <Limits value={balance.limits} />
                {balance.expiresAt && ` · до ${formatDate(balance.expiresAt)}`}
              </span>
            )}
          </span>
        )}
      </AppPageHeader>

      <div className="mt-10">
        <BalanceSection />
      </div>

      <div className="mt-10">
        <DeleteAccountSection />
      </div>
    </Container>
  )
}

function BalanceSection() {
  const { data: balance, isLoading } = useBalance()
  const usage = useUsage()

  return (
    <section>
      <h2 className="text-ink text-[21px] font-bold">Лимиты</h2>

      {isLoading && (
        <div role="status" className="mt-4">
          <span className="sr-only">Загрузка баланса…</span>
          <Skeleton className="h-5 w-40" />
          <Skeleton className="mt-3 h-4 w-64" />
        </div>
      )}

      {balance && <BalanceCard balance={balance} />}

      {usage.data && <UsageHistory events={usage.data.events} />}

      {usage.isError && (
        <p className="text-dim mt-4 text-sm">
          История операций временно недоступна.
        </p>
      )}

      <p className="text-dim mt-5 text-sm">
        Пополнить баланс можно на странице{' '}
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

function BalanceCard({ balance }: { balance: Balance }) {
  const low = balance.limits < OPERATION_COST.interview
  return (
    <div className="border-line bg-card mt-5 rounded-xl border p-5">
      <Eyebrow>Баланс</Eyebrow>
      <p className="mt-2">
        <span
          className={cn(
            'text-[28px] font-extrabold tracking-[-0.02em] tabular-nums',
            low ? 'text-star' : 'text-ink',
          )}
        >
          <Limits value={balance.limits} />
        </span>
      </p>
      <p className="text-dim mt-2.5 text-[12.5px]">
        {balance.expiresAt
          ? `Действуют до ${formatDate(balance.expiresAt)}`
          : 'Лимитов нет'}
        {' · '}интервью {OPERATION_COST.interview}, тренировка{' '}
        {OPERATION_COST.training}, эталонный ответ {OPERATION_COST.reference}
      </p>
    </div>
  )
}

function deltaText(event: UsageEvent) {
  const sign = event.kind === 'SPEND' ? '−' : '+'
  return <Limits value={event.delta} prefix={sign} />
}

function formatEventDate(iso: string): string {
  const time = new Date(iso).toLocaleTimeString('ru-RU', {
    hour: '2-digit',
    minute: '2-digit',
  })
  return `${formatDay(iso)}, ${time}`
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

  const filtered =
    filter === 'ALL' ? events : events.filter((e) => e.kind === filter)
  const visible = expanded ? filtered : filtered.slice(0, HISTORY_PREVIEW)

  const count = (value: KindFilter) =>
    value === 'ALL'
      ? events.length
      : events.filter((e) => e.kind === value).length

  return (
    <div className="mt-7">
      <h3 className="text-ink text-[15px] font-semibold">История операций</h3>

      {events.length === 0 ? (
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
            {visible.map((row, i) => (
              <li
                key={i}
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
