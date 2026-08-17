import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import type { Plan } from '@/content/plans'
import { cn } from '@/lib/cn'

function GiftIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="15"
      height="15"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0"
    >
      <path d="M20 12v10H4V12" />
      <path d="M2 7h20v5H2z" />
      <path d="M12 22V7" />
      <path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z" />
      <path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z" />
    </svg>
  )
}

interface PlanCardProps {
  plan: Plan
  features: string[]
  to: string
  /** Обработчик CTA вместо ссылки — например, запуск оплаты. */
  onSelect?: () => void
  disabled?: boolean
  className?: string
}

/** Карточка тарифа: цена, состав и переход к оформлению. */
export function PlanCard({
  plan,
  features,
  to,
  onSelect,
  disabled,
  className,
}: PlanCardProps) {
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
      <h3 className="text-ink text-[22px] font-bold">{plan.name}</h3>
      {plan.oldPrice && (
        <p className="mt-4.5 flex items-center gap-2.5">
          <s className="text-dim text-[15px] tabular-nums">{plan.oldPrice}</s>
          <span className="bg-violet/13 border-violet/22 text-violet-strong rounded-full border px-2.5 py-[2px] text-xs font-bold whitespace-nowrap">
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
      {plan.promo && (
        <p className="bg-violet/12 border-violet/25 text-violet-strong mt-4.5 flex items-center gap-2.5 rounded-lg border px-3.5 py-[9px] text-[13px] leading-snug font-semibold">
          <GiftIcon />
          {plan.promo}
        </p>
      )}
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
      {onSelect ? (
        <button
          type="button"
          onClick={onSelect}
          disabled={disabled}
          className={buttonClasses({
            variant: plan.featured ? 'primary' : 'secondary',
            className: 'mt-6.5 w-full',
          })}
        >
          {plan.cta}
        </button>
      ) : (
        <Link
          to={to}
          className={buttonClasses({
            variant: plan.featured ? 'primary' : 'secondary',
            className: 'mt-6.5 w-full',
          })}
        >
          {plan.cta}
        </Link>
      )}
    </div>
  )
}
