import { cn } from '@/lib/cn'

interface LogoProps {
  className?: string
}

/** Словесный логотип. Точка-акцент — отсылка к «правке на полях». */
export function Logo({ className }: LogoProps) {
  return (
    <span
      translate="no"
      className={cn(
        'font-display text-ink text-[1.5625rem] font-semibold tracking-tight',
        className,
      )}
    >
      workbit<span className="text-accent">.</span>
    </span>
  )
}
