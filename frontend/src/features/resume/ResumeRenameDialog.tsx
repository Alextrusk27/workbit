import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Alert } from '@/components/ui/Alert'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Field } from '@/components/ui/Field'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useModalA11y } from '@/lib/useModalA11y'
import type { Resume } from './api'
import { resumeErrorMessage } from './errors'
import { useRenameResume } from './useResumes'

const MAX_NAME_LENGTH = 100

export function ResumeRenameDialog({
  resume,
  open,
  onClose,
}: {
  resume: Resume
  open: boolean
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
            aria-label="Переименовать резюме"
            className={cn(modalPanelClasses, 'w-full max-w-[420px]')}
          >
            <RenameForm resume={resume} onClose={onClose} />
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

/** Смонтирована только пока модалка открыта: поле и ошибка мутации каждый раз
 *  начинаются с текущего названия. */
function RenameForm({
  resume,
  onClose,
}: {
  resume: Resume
  onClose: () => void
}) {
  const rename = useRenameResume()
  const [name, setName] = useState(resume.name)
  const trimmed = name.trim()

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!trimmed) return
    rename.mutate({ id: resume.id, name: trimmed }, { onSuccess: onClose })
  }

  return (
    <form onSubmit={onSubmit}>
      <h3 className="text-ink text-[17px] font-bold">Переименовать резюме</h3>
      <Field
        label="Название"
        className="mt-4"
        value={name}
        maxLength={MAX_NAME_LENGTH}
        autoComplete="off"
        autoFocus
        onChange={(e) => setName(e.target.value)}
      />
      {rename.isError && (
        <div className="mt-4">
          <Alert>{resumeErrorMessage(rename.error)}</Alert>
        </div>
      )}
      <div className="mt-[22px] flex flex-col-reverse gap-2.5 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={onClose}
          className={buttonClasses({ variant: 'secondary', size: 'sm' })}
        >
          Отмена
        </button>
        <button
          type="submit"
          disabled={!trimmed || rename.isPending}
          className={buttonClasses({ variant: 'primary', size: 'sm' })}
        >
          Сохранить
        </button>
      </div>
    </form>
  )
}
