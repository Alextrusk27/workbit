import { cn } from '@/lib/cn'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'sm' | 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-semibold whitespace-nowrap ' +
  'touch-manipulation transition disabled:opacity-45 disabled:pointer-events-none ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo'

const variants: Record<ButtonVariant, string> = {
  primary:
    'bg-grad text-white shadow-btn hover:-translate-y-px hover:shadow-btn-hover',
  secondary:
    'border border-line bg-glass text-ink hover:bg-glass-hover hover:border-glass-line',
  ghost: 'bg-transparent text-muted hover:bg-glass hover:text-ink',
  danger: 'border border-danger/40 text-danger hover:bg-danger/8',
}

const sizes: Record<ButtonSize, string> = {
  sm: 'h-10 px-[18px] text-sm',
  md: 'h-11 px-6 text-[15px]',
  lg: 'h-12 px-7 text-base',
}

/** Классы кнопки без элемента — чтобы одинаково оформлять `<button>` и ссылки
 *  (`<Link>`): кнопка внутри ссылки — невалидный HTML. */
export function buttonClasses({
  variant = 'primary',
  size = 'md',
  className,
}: {
  variant?: ButtonVariant
  size?: ButtonSize
  className?: string
} = {}): string {
  return cn(base, variants[variant], sizes[size], className)
}
