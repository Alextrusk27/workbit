import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { Pack } from '@/content/limits'
import { cn } from '@/lib/cn'

interface PlanCardProps {
  plan: Pack
  to: string
  className?: string
  /** Уровень заголовка карточки в иерархии страницы. */
  heading?: 'h2' | 'h3'
}

/** Карточка пакета: цена, состав и переход к оформлению. */
export function PlanCard({
  plan,
  to,
  className,
  heading: Heading = 'h3',
}: PlanCardProps) {
  return (
    <div
      className={cn(
        'border-line bg-card relative flex h-full flex-col rounded-2xl border p-8 sm:px-[30px]',
        className,
      )}
    >
      <Heading className="text-ink text-[22px] font-bold">{plan.name}</Heading>
      <p className="text-ink mt-4.5 text-[40px] leading-none font-extrabold tracking-[-0.03em] tabular-nums">
        {plan.price}
      </p>
      <ul className="mt-5.5 flex grow flex-col gap-2.5">
        {plan.features.map((f) => (
          <li key={f} className="text-muted flex gap-2.5 text-[14.5px]">
            <span aria-hidden className="text-indigo shrink-0 font-bold">
              ✓
            </span>
            {f}
          </li>
        ))}
      </ul>
      <Link
        to={to}
        className={buttonClasses({
          variant: 'secondary',
          className: 'mt-6.5 w-full',
        })}
      >
        {plan.cta}
      </Link>
    </div>
  )
}
