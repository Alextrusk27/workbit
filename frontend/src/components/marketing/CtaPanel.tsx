import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Финальный призыв: подсвеченная панель с кнопками. */
export function CtaPanel({
  title,
  children,
  actions,
  wide = false,
  compact = false,
}: {
  title: string
  children?: ReactNode
  actions: ReactNode
  wide?: boolean
  compact?: boolean
}) {
  return (
    <div
      className={cn(
        'border-violet/30 relative overflow-hidden rounded-3xl border bg-[linear-gradient(135deg,rgba(99,102,241,0.16),rgba(139,92,246,0.12)_60%,rgba(103,232,249,0.06))] text-center',
        compact ? 'px-6 py-9 sm:px-10 sm:py-11' : 'px-8 py-11 sm:py-18',
      )}
    >
      <h2
        className={cn(
          'text-ink',
          compact
            ? 'text-[clamp(24px,2.6vw,30px)] leading-tight'
            : 'text-[clamp(28px,3.6vw,40px)]',
        )}
      >
        {title}
      </h2>
      {children && (
        <p
          className={cn(
            'text-muted mx-auto mt-3 text-[17px]',
            !wide && !compact && 'max-w-[46ch]',
          )}
        >
          {children}
        </p>
      )}
      <div className="mt-7.5 flex flex-wrap justify-center gap-3.5">
        {actions}
      </div>
    </div>
  )
}
