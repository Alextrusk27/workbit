import type { ReactNode } from 'react'
import { useCallback, useMemo, useState } from 'react'
import { LimitsCreditedModal } from '@/components/app/LimitsCreditedModal'
import { WelcomeContext, useWelcome } from '@/features/auth/useWelcome'

export function WelcomeProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const show = useCallback(() => setOpen(true), [])
  const dismiss = useCallback(() => setOpen(false), [])
  const value = useMemo(() => ({ open, show, dismiss }), [open, show, dismiss])
  return (
    <WelcomeContext.Provider value={value}>{children}</WelcomeContext.Provider>
  )
}

/** Модалка о приветственных лимитах после их начисления (`welcomeGranted` из
 *  `/auth/verify-code`): та же, что после оплаты, зачисление берёт из истории. */
export function WelcomeModal() {
  const { open, dismiss } = useWelcome()
  return (
    <LimitsCreditedModal
      open={open}
      title="Добро пожаловать"
      onClose={dismiss}
    />
  )
}
