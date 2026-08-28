import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/** Карточка фичи или шага: иконка либо номер, заголовок, описание.
 *  `sm` — компактный вариант для малых карточек bento-сетки. */
export function FeatureCard({
  icon,
  title,
  size = 'md',
  children,
}: {
  icon: ReactNode
  title: string
  size?: 'md' | 'sm'
  children: ReactNode
}) {
  const sm = size === 'sm'
  return (
    <div
      className={cn(
        'border-line bg-card hover:border-line-hover h-full border transition hover:-translate-y-[3px]',
        sm ? 'rounded-2xl p-6' : 'rounded-xl px-6.5 py-7',
      )}
    >
      <div
        className={cn(
          'border-indigo/25 bg-indigo/12 text-indigo grid place-items-center rounded-lg border font-bold',
          sm ? 'mb-3.5 size-10 text-[15px]' : 'mb-4.5 size-11 text-[17px]',
        )}
      >
        {icon}
      </div>
      <h3
        className={cn(
          'text-ink font-semibold tracking-[-0.01em]',
          sm ? 'text-base' : 'text-[17px]',
        )}
      >
        {title}
      </h3>
      <p
        className={cn(
          'text-muted',
          sm ? 'mt-[7px] text-[13.5px]' : 'mt-2 text-[14.5px]',
        )}
      >
        {children}
      </p>
    </div>
  )
}
