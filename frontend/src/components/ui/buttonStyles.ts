import { cn } from '@/lib/cn'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost'
export type ButtonSize = 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 font-medium rounded-md ' +
  'transition-colors duration-150 disabled:opacity-50 disabled:pointer-events-none ' +
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent'

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-paper hover:bg-accent-hover',
  secondary:
    'border border-rule bg-transparent text-ink hover:bg-paper-2 hover:border-ink/30',
  ghost: 'bg-transparent text-ink hover:bg-paper-2',
}

const sizes: Record<ButtonSize, string> = {
  md: 'h-10 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
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
