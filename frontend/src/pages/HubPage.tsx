import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { PaymentSuccessModal } from '@/components/app/PaymentSuccessModal'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Limits } from '@/components/ui/LimitIcon'
import {
  PAYMENT_ID_KEY,
  billingKeys,
  useBalance,
  usePayment,
} from '@/features/billing/useBilling'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { formatDate } from '@/lib/dates'
import { reachGoal } from '@/lib/metrika'
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

function BalanceLine() {
  const { data } = useBalance()
  const openTopUp = useTopUpModal()
  if (!data) return null

  return (
    <p className="text-dim mt-10 text-[13.5px]">
      Лимиты:{' '}
      <span className="text-ink font-semibold tabular-nums">
        <Limits value={data.limits} />
      </span>
      {data.expiresAt && ` · действуют до ${formatDate(data.expiresAt)}`} ·{' '}
      <button
        type="button"
        onClick={() => openTopUp()}
        className="text-indigo hover:text-violet transition-colors"
      >
        Пополнить
      </button>
    </p>
  )
}

export function HubPage() {
  usePageTitle('Рабочий стол')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const openTopUp = useTopUpModal()
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
    navigate('/app', { replace: true })
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
    <Container>
      <AppPageHeader eyebrow="Рабочий стол" title="С чего начнём?">
        Интервью готовит к конкретной вакансии с hh.ru и оценивает шансы на
        оффер. Тренажёр прокачивает один навык под твою профессию и уровень.
      </AppPageHeader>

      {showFailed && (
        <div className="mt-8 max-w-[560px]">
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

      <div className="mt-10 grid gap-5 sm:grid-cols-2">
        <SectionCard
          to="/app/interview"
          eyebrow="Интервью"
          title="Интервью под вакансию"
          description="Собеседование под конкретную вакансию с hh.ru. Вопросы по её требованиям, а в конце — разбор и вероятность оффера."
        />
        <SectionCard
          to="/app/training"
          eyebrow="Тренажёр"
          title="Тренировка навыка"
          description="Один навык за сессию: вопросы под навык, профессию и уровень сложности. Отвечай по одному, разбор с оценками придёт в конце."
        />
      </div>

      <BalanceLine />

      {paid && !paymentFailed && (
        <PaymentSuccessModal
          open={paymentOpen}
          pending={!!paymentId && payment?.status !== 'PAID'}
          onClose={() => setPaymentOpen(false)}
        />
      )}
    </Container>
  )
}
