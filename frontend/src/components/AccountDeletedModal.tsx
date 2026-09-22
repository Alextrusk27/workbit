import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { CheckCircle } from '@/components/ui/CheckCircle'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'

/** Модалка «Аккаунт удалён»: `useDeleteAccount` уводит на главную с
 *  `state.accountDeleted`, флаг из истории тут же чистится replace-навигацией,
 *  чтобы после перезагрузки модалка не всплыла снова. */
export function AccountDeletedModal() {
  const location = useLocation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const panelRef = useModalA11y(open)
  const deleted = !!(location.state as { accountDeleted?: boolean } | null)
    ?.accountDeleted

  useEffect(() => {
    if (!deleted) return
    setOpen(true)
    void navigate(location.pathname, { replace: true })
  }, [deleted, location.pathname, navigate])

  const close = () => setOpen(false)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open])

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
            if (e.target === e.currentTarget) close()
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
            aria-label="Аккаунт удалён"
            className={cn(
              modalPanelClasses,
              'w-full max-w-[440px] text-center',
            )}
          >
            <CheckCircle />
            <h3 className="text-ink mt-[18px] text-[20px] font-bold">
              Аккаунт удалён
            </h3>
            <Button autoFocus className="mt-5 w-full" onClick={close}>
              Закрыть
            </Button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
