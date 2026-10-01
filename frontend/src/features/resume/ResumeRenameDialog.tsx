import { useEffect, useState, type FormEvent } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { IconPencil } from '@/components/marketing/icons'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
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
            className={cn(
              modalPanelClasses,
              'w-full max-w-[440px] text-center',
            )}
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
      <span className="bg-indigo/14 text-indigo mx-auto flex size-14 items-center justify-center rounded-full">
        <IconPencil className="size-[26px]" />
      </span>
      <h3 className="text-ink mt-[18px] text-[20px] font-bold">
        Переименовать резюме
      </h3>
      <Field
        label="Название"
        className="mt-5 text-left"
        value={name}
        maxLength={MAX_NAME_LENGTH}
        autoComplete="off"
        autoFocus
        onChange={(e) => setName(e.target.value)}
      />
      {rename.isError && (
        <div className="mt-4 text-left">
          <Alert>{resumeErrorMessage(rename.error)}</Alert>
        </div>
      )}
      <Button
        type="submit"
        disabled={!trimmed || rename.isPending}
        className="mt-5 w-full"
      >
        Сохранить
      </Button>
      <Button variant="ghost" className="mt-2 w-full" onClick={onClose}>
        Отмена
      </Button>
    </form>
  )
}
