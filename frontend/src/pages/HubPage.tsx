import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { PaymentSuccessModal } from '@/components/app/PaymentSuccessModal'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { PLAN_LABELS } from '@/features/billing/labels'
import {
  PAYMENT_ID_KEY,
  billingKeys,
  usePayment,
  useQuota,
} from '@/features/billing/useBilling'
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

function PlanLine() {
  const { data } = useQuota()
  if (!data) return null

  const until = data.planExpiresAt
    ? ` до ${new Date(data.planExpiresAt).toLocaleDateString('ru-RU', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      })}`
    : ''

  return (
    <p className="text-dim mt-10 text-[13.5px]">
      Тариф:{' '}
      <span className="text-ink font-semibold">
        {PLAN_LABELS[data.plan]}
        {until}
      </span>
      <span className="tabular-nums">
        {' '}
        · осталось интервью: {data.planInterviewsLeft}, тренировок:{' '}
        {data.planTrainingsLeft}
      </span>{' '}
      ·{' '}
      <Link
        to="/pricing"
        className="text-indigo hover:text-violet transition-colors"
      >
        Тарифы
      </Link>
    </p>
  )
}

export function HubPage() {
  usePageTitle('Личный кабинет')
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [paid] = useState(() => searchParams.get('payment') === 'ok')
  const [failed] = useState(() => searchParams.get('payment') === 'fail')
  const [paymentOpen, setPaymentOpen] = useState(paid)
  const [paymentId] = useState(() =>
    paid ? sessionStorage.getItem(PAYMENT_ID_KEY) : null,
  )
  const { data: payment } = usePayment(paymentId)

  useEffect(() => {
    if (!paid && !failed) return
    if (failed) sessionStorage.removeItem(PAYMENT_ID_KEY)
    qc.invalidateQueries({ queryKey: billingKeys.quota })
    navigate('/app', { replace: true })
  }, [paid, failed, navigate, qc])

  useEffect(() => {
    if (payment?.status !== 'PAID') return
    sessionStorage.removeItem(PAYMENT_ID_KEY)
    qc.invalidateQueries({ queryKey: billingKeys.quota })
    qc.invalidateQueries({ queryKey: billingKeys.usage })
  }, [payment?.status, qc])

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

      {failed && (
        <div className="mt-8 max-w-[560px]">
          <Alert>
            Оплата не прошла, деньги не списаны. Попробуйте ещё раз на{' '}
            <Link
              to="/pricing"
              className="underline underline-offset-2 transition-colors"
            >
              странице тарифов
            </Link>
            .
          </Alert>
        </div>
      )}

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

      <PlanLine />

      {paid && (
        <PaymentSuccessModal
          open={paymentOpen}
          onClose={() => setPaymentOpen(false)}
        />
      )}
    </Container>
  )
}
