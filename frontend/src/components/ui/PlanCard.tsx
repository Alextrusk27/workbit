import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { Plan } from '@/content/plans'
import { cn } from '@/lib/cn'

interface PlanCardProps {
  plan: Plan
  features: string[]
  to: string
  className?: string
}

/** Карточка тарифа: цена, состав и переход к оформлению. */
export function PlanCard({ plan, features, to, className }: PlanCardProps) {
  return (
    <div
      className={cn(
        'relative flex h-full flex-col rounded-2xl border p-8 sm:px-[30px]',
        plan.featured
          ? 'border-violet/50 bg-grad-plan shadow-plan'
          : 'border-line bg-card',
        className,
      )}
    >
      {plan.featured && (
        <span className="bg-grad absolute -top-3 left-1/2 -translate-x-1/2 rounded-full px-3.5 py-[5px] text-xs font-semibold whitespace-nowrap text-white">
          Популярный
        </span>
      )}
      <h3 className="text-ink text-[19px] font-bold">{plan.name}</h3>
      <p className="text-muted mt-1 text-sm">{plan.audience}</p>
      {plan.oldPrice && (
        <p className="mt-4.5 flex items-center gap-2.5">
          <s className="text-dim text-[15px] tabular-nums">{plan.oldPrice}</s>
          <span className="bg-grad rounded-full px-2.5 py-[3px] text-xs font-semibold whitespace-nowrap text-white">
            {plan.discount}
          </span>
        </p>
      )}
      <p
        className={cn(
          'text-ink text-[40px] leading-none font-extrabold tracking-[-0.03em] tabular-nums',
          plan.oldPrice ? 'mt-2' : 'mt-4.5',
        )}
      >
        {plan.price}
        <span className="text-muted ml-1.5 text-[15px] font-medium tracking-normal">
          {plan.period}
        </span>
      </p>
      <ul className="mt-5.5 flex grow flex-col gap-2.5">
        {features.map((f) => (
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
          variant: plan.featured ? 'primary' : 'secondary',
          className: 'mt-6.5 w-full',
        })}
      >
        {plan.cta}
      </Link>
    </div>
  )
}
