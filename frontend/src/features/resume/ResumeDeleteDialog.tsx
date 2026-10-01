import { useEffect } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { IconFile } from '@/components/marketing/icons'
import { Button } from '@/components/ui/Button'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'
import type { Resume } from './api'

export function ResumeDeleteDialog({
  resume,
  open,
  onConfirm,
  onClose,
}: {
  resume: Resume
  open: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  const panelRef = useModalA11y(open)
  const title = `Удалить резюме «${resume.name}»?`

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
            className={cn(
              modalPanelClasses,
              'w-full max-w-[440px] text-center',
            )}
          >
            <span className="bg-danger/14 text-danger mx-auto flex size-14 items-center justify-center rounded-full">
              <IconFile className="size-[26px]" />
            </span>
            <h3 className="text-ink mt-[18px] text-[20px] font-bold break-words">
              {title}
            </h3>
            <Button
              variant="danger-solid"
              className="mt-5 w-full"
              onClick={onConfirm}
            >
              Удалить
            </Button>
            <Button
              variant="ghost"
              autoFocus
              className="mt-2 w-full"
              onClick={onClose}
            >
              Отмена
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
