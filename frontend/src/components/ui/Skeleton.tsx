import { cn } from '@/lib/cn'

/** Пульсирующий плейсхолдер загрузки. reduced-motion гасит пульс глобальным правилом. */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      aria-hidden
      className={cn('bg-glass-hover animate-pulse rounded-md', className)}
    />
  )
}
