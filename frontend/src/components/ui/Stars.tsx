import { motion } from 'motion/react'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'

const STAR =
  'M12 2 L15.09 8.26 L22 9.27 L17 14.14 L18.18 21.02 L12 17.77 L5.82 21.02 L7 14.14 L2 9.27 L8.91 8.26 Z'

function Row({ count }: { count: number }) {
  return (
    <span className="flex shrink-0 gap-[0.1em]">
      {Array.from({ length: count }, (_, i) => (
        <svg
          key={i}
          viewBox="0 0 24 24"
          fill="currentColor"
          aria-hidden="true"
          className="size-[1em]"
        >
          <path d={STAR} />
        </svg>
      ))}
    </span>
  )
}

/** Оценка звёздами с дробным заполнением. `animate` — заполнение от нуля при
 *  появлении (сигнатурный штрих разбора). */
export function Stars({
  value,
  max = 5,
  className,
  animate = false,
}: {
  value: number
  max?: number
  className?: string
  animate?: boolean
}) {
  const pct = Math.max(0, Math.min(1, value / max)) * 100
  const rounded = Math.round(value * 2) / 2
  const filled = `inset(0 ${100 - pct}% 0 0)`

  return (
    <span
      role="img"
      aria-label={`Оценка ${String(rounded).replace('.', ',')} из ${max}`}
      className={cn(
        'text-star relative inline-flex align-middle leading-none',
        className,
      )}
    >
      <span className="text-star-off flex">
        <Row count={max} />
      </span>
      <motion.span
        className="absolute inset-0 flex"
        initial={animate ? { clipPath: 'inset(0 100% 0 0)' } : false}
        animate={{ clipPath: filled }}
        transition={{
          duration: motionTokens.duration.slow,
          ease: motionTokens.easing.smooth,
        }}
      >
        <Row count={max} />
      </motion.span>
    </span>
  )
}
