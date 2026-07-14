import type { ReactNode } from 'react'
import { Stars } from '@/components/ui/Stars'
import { useReveal } from '@/lib/useReveal'
import { cn } from '@/lib/cn'

interface MarginNoteProps {
  children: ReactNode
  /** Балл 1–5, если пометка несёт оценку. */
  score?: number
  className?: string
}

/** Сигнатура продукта: пометка рецензента «на полях» — как правка синим
 *  карандашом. При появлении во вьюпорте штрих карандаша прочерчивается сверху
 *  вниз, а оценка «дорисовывается» звёздами. Используется для подачи фидбэка
 *  LLM (feedback/score). */
export function MarginNote({ children, score, className }: MarginNoteProps) {
  const { ref, shown } = useReveal<HTMLElement>()
  return (
    <aside
      ref={ref}
      className={cn(
        'text-edit font-display relative pl-3 text-sm italic',
        className,
      )}
    >
      <span
        aria-hidden
        className="bg-edit absolute top-0 left-0 h-full w-0.5 origin-top"
        style={{
          transform: shown ? 'scaleY(1)' : 'scaleY(0)',
          transition: 'transform 0.6s cubic-bezier(0.22, 1, 0.36, 1)',
        }}
      />
      {score !== undefined && (
        <span className="mb-1 block min-h-[1.4em] text-sm not-italic">
          {shown && <Stars value={score} animate />}
        </span>
      )}
      {children}
    </aside>
  )
}
