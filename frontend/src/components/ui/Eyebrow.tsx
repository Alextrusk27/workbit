import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Надзаголовок: мелкая разрядка над крупным заголовком блока. */
export function Eyebrow({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <p
      className={cn(
        'text-dim text-xs font-semibold tracking-[0.14em] uppercase',
        className,
      )}
    >
      {children}
    </p>
  )
}
