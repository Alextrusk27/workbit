import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Карточка тарифа-переключатель: клик/Enter/Space выбирает план, выбранный
 *  подсвечивается акцентной рамкой. Внутреннее наполнение и отступы (padding)
 *  задаёт вызывающий через children и className. */
export function PlanCard({
  selected,
  onSelect,
  className,
  children,
}: {
  selected: boolean
  onSelect: () => void
  className?: string
  children: ReactNode
}) {
  return (
    <div
      role="radio"
      aria-checked={selected}
      tabIndex={0}
      onClick={onSelect}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onSelect()
        }
      }}
      className={cn(
        'flex h-full cursor-pointer flex-col rounded-lg border transition-colors',
        'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
        selected
          ? 'border-accent bg-paper-2/50 ring-accent ring-1'
          : 'border-rule hover:border-ink/30',
        className,
      )}
    >
      {children}
    </div>
  )
}
