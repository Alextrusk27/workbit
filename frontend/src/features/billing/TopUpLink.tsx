import type { ReactNode } from 'react'
import { useTopUpModal } from '@/features/billing/useTopUpModal'

export function TopUpLink({
  children = 'Пополни баланс',
}: {
  children?: ReactNode
}) {
  const openTopUp = useTopUpModal()
  return (
    <button
      type="button"
      onClick={() => openTopUp()}
      className="underline underline-offset-2 transition-colors"
    >
      {children}
    </button>
  )
}
