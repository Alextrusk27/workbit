import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface MarginNoteProps {
  children: ReactNode
  /** Балл 0–10, если пометка несёт оценку. */
  score?: number
  className?: string
}

/** Сигнатура продукта: пометка рецензента «на полях» — как правка синим
 *  карандашом. Используется для подачи фидбэка LLM (feedback/score). */
export function MarginNote({ children, score, className }: MarginNoteProps) {
  return (
    <aside
      className={cn(
        'border-edit font-display text-edit border-l-2 pl-3 text-sm italic',
        className,
      )}
    >
      {score !== undefined && (
        <span className="mb-1 block font-mono text-xs font-medium tracking-wide not-italic">
          {score}/10
        </span>
      )}
      {children}
    </aside>
  )
}
