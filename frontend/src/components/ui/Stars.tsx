import { useEffect, useState } from 'react'
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

/** Оценка звёздами с дробным заполнением (шаг 0.5 и любой процент). Цвет
 *  заполненных звёзд — currentColor (наследуется от контекста), пустых — rule.
 *  `animate` — заполнить от 0 до значения при монтировании (сигнатурный штрих;
 *  по умолчанию выключено, чтобы списки оценок оставались спокойными). */
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
  const [width, setWidth] = useState(animate ? 0 : pct)

  useEffect(() => {
    if (!animate) {
      setWidth(pct)
      return
    }
    const id = requestAnimationFrame(() => setWidth(pct))
    return () => cancelAnimationFrame(id)
  }, [animate, pct])

  return (
    <span
      role="img"
      aria-label={`Оценка ${String(rounded).replace('.', ',')} из ${max}`}
      className={cn(
        'relative inline-flex align-middle leading-none',
        className,
      )}
    >
      <span className="text-rule flex">
        <Row count={max} />
      </span>
      <span
        className="absolute inset-0 flex overflow-hidden"
        style={{
          width: `${width}%`,
          transition: animate
            ? 'width 0.9s cubic-bezier(0.22, 1, 0.36, 1)'
            : undefined,
        }}
      >
        <Row count={max} />
      </span>
    </span>
  )
}
