import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type AlertTone = 'error' | 'success'

const tones: Record<AlertTone, string> = {
  error: 'border-danger/30 bg-danger/8 text-danger',
  success: 'border-ok/30 bg-ok/8 text-ok',
}

/** Небольшой баннер сообщения в формах. */
export function Alert({
  tone = 'error',
  children,
}: {
  tone?: AlertTone
  children: ReactNode
}) {
  return (
    <p
      role="alert"
      className={cn('rounded-md border px-4 py-3 text-sm', tones[tone])}
    >
      {children}
    </p>
  )
}
