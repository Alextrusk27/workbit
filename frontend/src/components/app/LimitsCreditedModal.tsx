import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { CheckCircle } from '@/components/ui/CheckCircle'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { Spinner } from '@/components/ui/Spinner'
import type { UsageEvent } from '@/features/billing/api'
import { useUsage } from '@/features/billing/useBilling'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'

const BATCH_WINDOW_MS = 10_000

function latestCreditBatch(events: UsageEvent[] | undefined): UsageEvent[] {
  if (!events) return []
  const start = events.findIndex((e) => e.kind === 'CREDIT')
  if (start < 0) return []
  const firstAt = new Date(events[start].at).getTime()
  const batch: UsageEvent[] = []
  for (let i = start; i < events.length; i++) {
    const event = events[i]
    if (event.kind !== 'CREDIT') break
    if (firstAt - new Date(event.at).getTime() > BATCH_WINDOW_MS) break
    batch.push(event)
  }
  return batch
}

function toCreditRows(batch: UsageEvent[]) {
  return [...batch].reverse().map((event) => ({
    key: event.label,
    name: event.label,
    delta: event.delta,
  }))
}

function PendingCircle() {
  return (
    <span className="bg-indigo/14 mx-auto flex size-14 items-center justify-center rounded-full">
      <Spinner className="align-baseline" />
    </span>
  )
}

/** Модалка о зачислении лимитов (оплата, приветственные, в будущем промокоды):
 *  зачисления из последней CREDIT-пачки истории операций и срок действия
 *  баланса. `pending` — оплату ещё не подтвердил webhook, показываем ожидание. */
export function LimitsCreditedModal({
  open,
  title,
  pending = false,
  footnote,
  onClose,
}: {
  open: boolean
  title: string
  pending?: boolean
  footnote?: ReactNode
  onClose: () => void
}) {
  const panelRef = useModalA11y(open)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: motionTokens.duration.fast }}
          className={cn(
            modalOverlayClasses,
            'flex items-center justify-center',
          )}
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
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-label={pending ? 'Проверяем оплату' : title}
            className={cn(
              modalPanelClasses,
              'w-full max-w-[440px] text-center',
            )}
          >
            {pending ? (
              <>
                <PendingCircle />
                <h3 className="text-ink mt-[18px] text-[20px] font-bold">
                  Проверяем оплату
                </h3>
                <p role="status" className="text-muted mt-2 text-[14.5px]">
                  Ждём подтверждения. Лимиты зачислятся сами, обычно это
                  занимает несколько секунд.
                </p>
              </>
            ) : (
              <>
                <CheckCircle />
                <h3 className="text-ink mt-[18px] text-[20px] font-bold">
                  {title}
                </h3>
                <CreditedSummary footnote={footnote} />
              </>
            )}
            <Button autoFocus className="mt-5 w-full" onClick={onClose}>
              Продолжить
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

function CreditedSummary({ footnote }: { footnote?: ReactNode }) {
  const { data: usage } = useUsage()
  const credits = toCreditRows(latestCreditBatch(usage?.events))

  return (
    <>
      {credits.length > 0 && (
        <div className="mt-5 flex flex-col gap-2">
          {credits.map((credit) => (
            <div
              key={credit.key}
              className="bg-glass border-line flex justify-between rounded-lg border px-4 py-2.5"
            >
              <span className="text-ink text-sm">{credit.name}</span>
              <span className="text-ok text-sm font-semibold tabular-nums">
                +{credit.delta}
              </span>
            </div>
          ))}
        </div>
      )}
      {footnote && <p className="text-dim mt-3.5 text-[12.5px]">{footnote}</p>}
    </>
  )
}
