import type { ReactNode } from 'react'
import { motion, useReducedMotion } from 'motion/react'
import { Stars } from '@/components/ui/Stars'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'

interface MarginNoteProps {
  children: ReactNode
  /** Балл 1–5, если пометка несёт оценку. */
  score?: number
  className?: string
}

/** Сигнатура продукта: пометка рецензента «на полях». Полоса прочерчивается
 *  сверху вниз при появлении, оценка дорисовывается звёздами. */
export function MarginNote({ children, score, className }: MarginNoteProps) {
  const reduce = useReducedMotion()
  return (
    <aside
      className={cn(
        'bg-glass text-muted relative rounded-r-lg py-3.5 pr-5 pl-4.5 text-sm',
        className,
      )}
    >
      <motion.span
        aria-hidden
        className="bg-indigo absolute top-0 left-0 h-full w-0.5 origin-top"
        initial={reduce ? false : { scaleY: 0 }}
        whileInView={{ scaleY: 1 }}
        viewport={{ once: true, margin: '-40px' }}
        transition={{
          duration: motionTokens.duration.slow,
          ease: motionTokens.easing.smooth,
        }}
      />
      {score !== undefined && (
        <span className="mb-1.5 flex items-center gap-2 text-[13px]">
          <Stars value={score} animate={!reduce} />
          <span className="text-dim">{score} из 5</span>
        </span>
      )}
      {children}
    </aside>
  )
}
