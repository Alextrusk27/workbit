import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { useAuth } from '@/features/auth/useAuth'
import type { Quota, UsageEvent, UsageTarget } from '@/features/billing/api'
import { PLAN_LABELS } from '@/features/billing/labels'
import { useQuota, useUsage } from '@/features/billing/useBilling'
import { motionTokens } from '@/lib/motion'
import { trainingsWord } from '@/lib/plural'

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

function packTitle(credit: UsageEvent): string {
  const unit =
    credit.target === 'INTERVIEW' ? 'интервью' : trainingsWord(credit.delta)
  return `+${credit.delta} ${unit} зачислены`
}

function packSubtitle(credit: UsageEvent, quota: Quota): string {
  if (credit.target === 'INTERVIEW') {
    const left = quota.planInterviewsLeft + quota.packInterviewsLeft
    return `Пакет не сгорает — теперь доступно ${left} интервью.`
  }
  const left = quota.planTrainingsLeft + quota.packTrainingsLeft
  const unit = trainingsWord(left)
  const verb =
    unit === 'тренировка'
      ? 'доступна'
      : unit === 'тренировки'
        ? 'доступны'
        : 'доступно'
  return `Пакет не сгорает — теперь ${verb} ${left} ${unit}.`
}

function CheckCircle({ size }: { size: 'lg' | 'md' }) {
  return (
    <span
      className={`bg-ok/14 mx-auto flex items-center justify-center rounded-full ${size === 'lg' ? 'size-14' : 'size-12'}`}
    >
      <svg
        viewBox="0 0 24 24"
        width={size === 'lg' ? 26 : 22}
        height={size === 'lg' ? 26 : 22}
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

/** Модалка после возврата с оплаты: тариф с зачислениями либо короткий
 *  вариант для пакета — по последнему зачислению из истории операций. */
export function PaymentSuccessModal({
  open,
  onClose,
}: {
  open: boolean
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
  const isPack = credits.length === 1 && quota

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
            aria-label="Оплата прошла"
            className="border-line bg-pop shadow-chat w-full max-w-[440px] rounded-2xl border p-7 text-center"
          >
            {isPack ? (
              <>
                <CheckCircle size="md" />
                <h3 className="text-ink mt-[18px] text-[18px] font-bold">
                  {packTitle(credits[0])}
                </h3>
                <p className="text-muted mt-2 text-[14.5px]">
                  {packSubtitle(credits[0], quota)}
                </p>
              </>
            ) : (
              <>
                <CheckCircle size="lg" />
                <h3 className="text-ink mt-[18px] text-[20px] font-bold">
                  Оплата прошла
                </h3>
                {quota && (
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
                {credits.length > 0 && (
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
                {user && (
                  <p className="text-dim mt-3.5 text-[12.5px]">
                    Чек отправили на {user.email}
                  </p>
                )}
              </>
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
