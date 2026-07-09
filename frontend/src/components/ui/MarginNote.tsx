import type { ReactNode } from 'react'
import { Stars } from '@/components/ui/Stars'
import { cn } from '@/lib/cn'

interface MarginNoteProps {
  children: ReactNode
  /** Балл 1–5, если пометка несёт оценку. */
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
        <span className="mb-1 block text-sm not-italic">
          <Stars value={score} />
        </span>
      )}
      {children}
    </aside>
  )
}
