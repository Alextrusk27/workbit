import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'

interface ConfirmDialogProps {
  open: boolean
  title: string
  text: string
  confirmLabel?: string
  onConfirm: () => void
  onClose: () => void
}

/** Модалка подтверждения опасного действия: стеклянный блюр-фон, «Отмена» и
 *  красная градиентная кнопка. Замена нативного confirm(). */
export function ConfirmDialog({
  open,
  title,
  text,
  confirmLabel = 'Удалить',
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
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
            aria-label={title}
            className="border-line bg-pop shadow-chat w-full max-w-[420px] rounded-2xl border p-7"
          >
            <h3 className="text-ink text-[17px] font-bold">{title}</h3>
            <p className="text-dim mt-2.5 text-[13.5px] leading-[1.55]">
              {text}
            </p>
            <div className="mt-[22px] flex justify-end gap-2.5">
              <button
                type="button"
                autoFocus
                onClick={onClose}
                className={buttonClasses({ variant: 'secondary', size: 'sm' })}
              >
                Отмена
              </button>
              <button
                type="button"
                onClick={onConfirm}
                className={cn(
                  buttonClasses({ size: 'sm' }),
                  'bg-[linear-gradient(135deg,#f87171,#ef4444)] shadow-none hover:shadow-none',
                )}
              >
                {confirmLabel}
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
