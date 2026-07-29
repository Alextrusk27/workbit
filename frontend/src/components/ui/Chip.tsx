import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface ChipProps {
  children: ReactNode
  selected?: boolean
  count?: number
  onClick?: () => void
  disabled?: boolean
  className?: string
}

/** Пилюля-переключатель: фильтры списков, выбор уровня, теги. */
export function Chip({
  children,
  selected = false,
  count,
  onClick,
  disabled,
  className,
}: ChipProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={selected}
      className={cn(
        'border-line touch-manipulation rounded-full border px-4 py-[7px] text-[13.5px] font-medium transition',
        'focus-visible:outline-indigo focus-visible:outline-2 focus-visible:outline-offset-2',
        'disabled:pointer-events-none disabled:opacity-45',
        selected
          ? 'border-indigo/35 bg-indigo/12 text-ink'
          : 'text-muted hover:border-glass-line hover:text-ink bg-transparent',
        className,
      )}
    >
      {children}
      {count !== undefined && (
        <span className="ml-1.5 tabular-nums opacity-60">{count}</span>
      )}
    </button>
  )
}
