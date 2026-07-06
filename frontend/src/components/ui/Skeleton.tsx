import { cn } from '@/lib/cn'

/** Пульсирующий плейсхолдер загрузки. reduced-motion гасит пульс глобальным правилом. */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn('bg-ink/8 animate-pulse rounded-md', className)}
    />
  )
}
