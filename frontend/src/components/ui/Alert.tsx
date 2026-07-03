import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

type AlertTone = 'error' | 'success'

const tones: Record<AlertTone, string> = {
  error: 'border-accent/40 bg-accent/5 text-ink',
  success: 'border-pine/40 bg-pine/5 text-ink',
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
      className={cn('rounded-md border px-3 py-2.5 text-sm', tones[tone])}
    >
      {children}
    </p>
  )
}
