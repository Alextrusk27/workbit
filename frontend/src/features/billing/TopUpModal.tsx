import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { IconClose } from '@/components/marketing/icons'
import { TopUpCard } from '@/components/marketing/TopUpCard'
import { Alert } from '@/components/ui/Alert'
import { Limits } from '@/components/ui/LimitIcon'
import { operations } from '@/content/limits'
import { useAuth } from '@/features/auth/useAuth'
import { useLoginModal } from '@/features/auth/useLoginModal'
import {
  TopUpModalContext,
  useTopUpModal,
} from '@/features/billing/useTopUpModal'
import { PAYMENT_ID_KEY, useCreatePayment } from '@/features/billing/useBilling'
import { getErrorMessage } from '@/lib/api'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'

interface TopUpModalState {
  open: boolean
  seq: number
  limits?: number
}

export function TopUpModalProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<TopUpModalState>({ open: false, seq: 0 })
  const open = useCallback(
    (limits?: number) =>
      setState((s) => ({ open: true, seq: s.seq + 1, limits })),
    [],
  )
  const close = useCallback(() => setState((s) => ({ ...s, open: false })), [])

  return (
    <TopUpModalContext.Provider value={open}>
      {children}
      <TopUpModal
        key={state.seq}
        open={state.open}
        initialLimits={state.limits}
        onClose={close}
      />
    </TopUpModalContext.Provider>
  )
}

function TopUpModal({
  open,
  initialLimits,
  onClose,
}: {
  open: boolean
  initialLimits?: number
  onClose: () => void
}) {
  const { isAuthenticated } = useAuth()
  const openLogin = useLoginModal()
  const openTopUp = useTopUpModal()
  const createPayment = useCreatePayment()
  const [error, setError] = useState<string | null>(null)
  const panelRef = useModalA11y(open)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  const buy = (limits: number) => {
    if (createPayment.isPending) return
    setError(null)
    createPayment.mutate(limits, {
      onSuccess: ({ paymentId, paymentUrl }) => {
        sessionStorage.setItem(PAYMENT_ID_KEY, paymentId)
        window.location.assign(paymentUrl)
      },
      onError: (e) => setError(getErrorMessage(e)),
    })
  }

  const login = (limits: number) => {
    onClose()
    openLogin({ onSuccess: () => openTopUp(limits) })
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: motionTokens.duration.fast }}
          className="fixed inset-0 z-100 overflow-y-auto bg-[rgba(6,9,20,0.65)] p-5 backdrop-blur-[6px]"
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
            aria-label="Пополнение баланса"
            className="mx-auto my-auto flex min-h-full w-full max-w-[520px] flex-col justify-center gap-4"
            onClick={(e) => {
              if (e.target === e.currentTarget) onClose()
            }}
          >
            {error && <Alert>{error}</Alert>}
            <div className="bg-pop shadow-chat relative rounded-2xl">
              <TopUpCard
                initialLimits={initialLimits}
                onBuy={isAuthenticated ? buy : undefined}
                onLogin={isAuthenticated ? undefined : login}
                ctaLabel={
                  isAuthenticated ? undefined : 'Войти, чтобы пополнить'
                }
                disabled={createPayment.isPending}
              >
                <ul className="divide-divider mt-5 divide-y">
                  {operations.map((op) => (
                    <li
                      key={op.name}
                      className="flex items-baseline justify-between gap-6 py-2"
                    >
                      <p className="text-muted text-[14px]">{op.name}</p>
                      <p className="text-ink shrink-0 text-[14px] font-semibold whitespace-nowrap tabular-nums">
                        <Limits value={op.cost} />
                      </p>
                    </li>
                  ))}
                </ul>
                <p className="text-dim mt-3 text-[12.5px]">
                  Лимиты действуют 3 месяца с момента последней покупки.
                </p>
              </TopUpCard>
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть"
                className="text-muted hover:text-ink hover:bg-indigo/8 absolute top-4 right-4 inline-flex size-9 items-center justify-center rounded-md transition-colors"
              >
                <IconClose className="size-5" />
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
