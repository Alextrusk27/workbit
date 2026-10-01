import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { LimitIcon } from '@/components/ui/LimitIcon'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { isNotEnoughLimits } from '@/features/billing/errors'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'

export function NotEnoughLimitsDialog({
  error,
  onClose,
}: {
  error: unknown
  onClose: () => void
}) {
  const open = isNotEnoughLimits(error)
  const openTopUp = useTopUpModal()
  const panelRef = useModalA11y(open)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  const topUp = () => {
    onClose()
    openTopUp()
  }

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
            aria-label="Не хватает лимитов"
            className={cn(
              modalPanelClasses,
              'w-full max-w-[440px] text-center',
            )}
          >
            <span className="bg-indigo/14 text-indigo mx-auto flex size-14 items-center justify-center rounded-full text-[26px]">
              <LimitIcon />
            </span>
            <h3 className="text-ink mt-[18px] text-[20px] font-bold">
              Не хватает лимитов
            </h3>
            <Button autoFocus className="mt-5 w-full" onClick={topUp}>
              Пополнить баланс
            </Button>
            <Button variant="ghost" className="mt-2 w-full" onClick={onClose}>
              Не сейчас
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
