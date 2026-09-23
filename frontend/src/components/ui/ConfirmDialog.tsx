import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'
import { useModalA11y } from '@/lib/useModalA11y'

interface ConfirmDialogProps {
  open: boolean
  title: string
  text: string
  confirmLabel?: ReactNode
  confirmVariant?: 'danger-solid' | 'primary'
  onConfirm: () => void
  onClose: () => void
}

/** Модалка подтверждения действия: стеклянный блюр-фон, «Отмена» и кнопка
 *  подтверждения — красная для удаления, фиолетовая для прочего. Замена
 *  нативного confirm(). */
export function ConfirmDialog({
  open,
  title,
  text,
  confirmLabel = 'Удалить',
  confirmVariant = 'danger-solid',
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
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
            aria-label={title}
            className={cn(modalPanelClasses, 'w-full max-w-[420px]')}
          >
            <h3 className="text-ink text-[17px] font-bold">{title}</h3>
            <p className="text-dim mt-2.5 text-[13.5px] leading-[1.55]">
              {text}
            </p>
            <div className="mt-[22px] flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
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
                className={buttonClasses({
                  variant: confirmVariant,
                  size: 'sm',
                })}
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
