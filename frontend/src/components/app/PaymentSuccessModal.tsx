import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { Spinner } from '@/components/ui/Spinner'
import { useAuth } from '@/features/auth/useAuth'
import type { UsageEvent, UsageTarget } from '@/features/billing/api'
import { PLAN_LABELS } from '@/features/billing/labels'
import { useQuota, useUsage } from '@/features/billing/useBilling'
import { motionTokens } from '@/lib/motion'

const TARGET_LABELS: Record<UsageTarget, string> = {
  INTERVIEW: 'AI-интервью',
  TRAINING: 'Тренировки',
}

function latestCreditBatch(events: UsageEvent[] | undefined): UsageEvent[] {
  const first = events?.find((e) => e.kind === 'CREDIT')
  if (!first || !events) return []
  return events.filter(
    (e) => e.kind === 'CREDIT' && e.at === first.at && e.label === first.label,
  )
}

function CheckCircle() {
  return (
    <span className="bg-ok/14 mx-auto flex size-14 items-center justify-center rounded-full">
      <svg
        viewBox="0 0 24 24"
        width={26}
        height={26}
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        className="text-ok"
        aria-hidden="true"
      >
        <path d="M20 6 9 17l-5-5" />
      </svg>
    </span>
  )
}

function PendingCircle() {
  return (
    <span className="bg-indigo/14 mx-auto flex size-14 items-center justify-center rounded-full">
      <Spinner className="align-baseline" />
    </span>
  )
}

/** Модалка после возврата с оплаты: тариф и зачисления
 *  из последней CREDIT-пачки истории операций. Пока оплату
 *  не подтвердил webhook, показывает ожидание вместо тарифа. */
export function PaymentSuccessModal({
  open,
  pending,
  onClose,
}: {
  open: boolean
  pending: boolean
  onClose: () => void
}) {
  const { user } = useAuth()
  const { data: quota } = useQuota()
  const { data: usage } = useUsage()

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  const credits = latestCreditBatch(usage?.events)

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: motionTokens.duration.fast }}
          className="fixed inset-0 z-100 flex items-center justify-center bg-[rgba(6,9,20,0.65)] p-5 backdrop-blur-[6px]"
          onClick={(e) => {
            if (e.target === e.currentTarget) onClose()
          }}
        >
          <motion.div
            initial={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            transition={{
              duration: motionTokens.duration.fast,
              ease: motionTokens.easing.smooth,
            }}
            role="dialog"
            aria-modal="true"
            aria-label={pending ? 'Проверяем оплату' : 'Оплата прошла'}
            className="border-line bg-pop shadow-chat w-full max-w-[440px] rounded-2xl border p-7 text-center"
          >
            {pending ? <PendingCircle /> : <CheckCircle />}
            <h3 className="text-ink mt-[18px] text-[20px] font-bold">
              {pending ? 'Проверяем оплату' : 'Оплата прошла'}
            </h3>
            {pending && (
              <p role="status" className="text-muted mt-2 text-[14.5px]">
                Ждём подтверждения. Тариф активируется сам, обычно это занимает
                несколько секунд.
              </p>
            )}
            {!pending && quota && (
              <p className="text-muted mt-2 text-[14.5px]">
                Тариф{' '}
                <span className="text-ink font-semibold">
                  {PLAN_LABELS[quota.plan]}
                </span>
                {quota.planExpiresAt &&
                  ` активен до ${new Date(
                    quota.planExpiresAt,
                  ).toLocaleDateString('ru-RU', {
                    day: 'numeric',
                    month: 'long',
                    year: 'numeric',
                  })}`}
              </p>
            )}
            {!pending && credits.length > 0 && (
              <div className="mt-5 flex flex-col gap-2">
                {credits.map((credit) => (
                  <div
                    key={credit.target}
                    className="bg-glass border-line flex justify-between rounded-lg border px-4 py-2.5"
                  >
                    <span className="text-ink text-sm">
                      {TARGET_LABELS[credit.target]}
                    </span>
                    <span className="text-ok text-sm font-semibold tabular-nums">
                      +{credit.delta}
                    </span>
                  </div>
                ))}
              </div>
            )}
            {!pending && user && (
              <p className="text-dim mt-3.5 text-[12.5px]">
                Чек отправили на {user.email}
              </p>
            )}
            <Button className="mt-5 w-full" onClick={onClose}>
              Продолжить
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
