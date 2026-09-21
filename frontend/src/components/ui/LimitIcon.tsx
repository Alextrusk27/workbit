import { cn } from '@/lib/cn'
import { limitsWord } from '@/lib/plural'

/** Знак лимитов — внутренней валюты Workbit: четырёхлучевая искра, «заряд на
 *  попытку». Одна форма, `currentColor`, читается в 16 px. Ставится после числа,
 *  как ₽: «20 ✦». Размер — от кегля (`1em`), переопределяется через `className`.
 *  Источник и варианты (заливка/обводка) — `docs/design/limit-glyphs/`. */
export function LimitIcon({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      className={cn('size-[1em] shrink-0', className)}
    >
      <path d="M12 2c1.5 5.5 4.5 8.5 10 10-5.5 1.5-8.5 4.5-10 10-1.5-5.5-4.5-8.5-10-10 5.5-1.5 8.5-4.5 10-10Z" />
    </svg>
  )
}

/** Число лимитов со знаком: «20 ✦». Для скринридера слово остаётся — «20 лимитов».
 *  `prefix` — знак операции в истории списаний («−», «+»). */
export function Limits({
  value,
  prefix,
  className,
}: {
  value: number
  prefix?: string
  className?: string
}) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-[0.22em] whitespace-nowrap tabular-nums',
        className,
      )}
    >
      <span>
        {prefix}
        {value}
      </span>
      <LimitIcon className="size-[0.9em]" />
      <span className="sr-only"> {limitsWord(value)}</span>
    </span>
  )
}
