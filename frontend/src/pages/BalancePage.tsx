import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { LimitsCreditedModal } from '@/components/app/LimitsCreditedModal'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Limits } from '@/components/ui/LimitIcon'
import { Skeleton } from '@/components/ui/Skeleton'
import { OPERATION_COST } from '@/content/limits'
import { useAuth } from '@/features/auth/useAuth'
import type { Balance, UsageEvent } from '@/features/billing/api'
import {
  PAYMENT_ID_KEY,
  billingKeys,
  useBalance,
  usePayment,
  useUsage,
} from '@/features/billing/useBilling'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { cn } from '@/lib/cn'
import { formatDate, formatDay } from '@/lib/dates'
import { reachGoal } from '@/lib/metrika'
import { usePageTitle } from '@/lib/usePageTitle'

export function BalancePage() {
  usePageTitle('Баланс')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const openTopUp = useTopUpModal()
  const { user } = useAuth()
  const [paid] = useState(() => searchParams.get('payment') === 'ok')
  const [failed] = useState(() => searchParams.get('payment') === 'fail')
  const [paymentOpen, setPaymentOpen] = useState(paid)
  const [paymentId] = useState(() =>
    paid ? sessionStorage.getItem(PAYMENT_ID_KEY) : null,
  )
  const { data: payment } = usePayment(paymentId)
  const paymentFailed = payment?.status === 'FAILED'
  const showFailed = failed || paymentFailed

  useEffect(() => {
    if (!paid && !failed) return
    if (failed) sessionStorage.removeItem(PAYMENT_ID_KEY)
    qc.invalidateQueries({ queryKey: billingKeys.quota })
    navigate('/app/balance', { replace: true })
  }, [paid, failed, navigate, qc])

  useEffect(() => {
    if (!paymentFailed) return
    sessionStorage.removeItem(PAYMENT_ID_KEY)
    setPaymentOpen(false)
  }, [paymentFailed])

  useEffect(() => {
    if (payment?.status !== 'PAID') return
    sessionStorage.removeItem(PAYMENT_ID_KEY)
    reachGoal('payment_success', {
      order_price: payment.amount,
      currency: 'RUB',
    })
    qc.invalidateQueries({ queryKey: billingKeys.quota })
    qc.invalidateQueries({ queryKey: billingKeys.usage })
  }, [payment?.status, payment?.amount, qc])

  return (
    <Container className="max-w-160">
      <AppPageHeader title="Баланс" />

      {showFailed && (
        <div className="mt-8">
          <Alert>
            Оплата не прошла, деньги не списаны.{' '}
            <button
              type="button"
              onClick={() => openTopUp()}
              className="underline underline-offset-2 transition-colors"
            >
              Попробовать ещё раз
            </button>
            .
          </Alert>
        </div>
      )}

      <div className="mt-10">
        <BalanceSection />
      </div>

      {paid && !paymentFailed && (
        <LimitsCreditedModal
          open={paymentOpen}
          title="Оплата прошла"
          pending={!!paymentId && payment?.status !== 'PAID'}
          footnote={user && `Чек отправили на ${user.email}`}
          onClose={() => setPaymentOpen(false)}
        />
      )}
    </Container>
  )
}

function BalanceSection() {
  const { data: balance, isLoading } = useBalance()
  const usage = useUsage()

  return (
    <section>
      {isLoading && (
        <div role="status">
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
    </section>
  )
}

function BalanceCard({ balance }: { balance: Balance }) {
  const openTopUp = useTopUpModal()
  const low = balance.limits < OPERATION_COST.interview
  return (
    <div className="border-line bg-card flex flex-wrap items-end justify-between gap-4 rounded-xl border p-5">
      <div>
        <Eyebrow>Остаток</Eyebrow>
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
            ? `Лимиты действуют до ${formatDate(balance.expiresAt)}`
            : 'Лимитов нет'}
        </p>
      </div>
      <Button onClick={() => openTopUp()} className="max-sm:w-full">
        Пополнить баланс
      </Button>
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
            {visible.map((row) => (
              <li
                key={`${row.at}|${row.operation}|${row.label}`}
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
