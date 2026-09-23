import { Link } from 'react-router-dom'
import { Limits } from '@/components/ui/LimitIcon'
import { OPERATION_COST } from '@/content/limits'
import { useBalance } from '@/features/billing/useBilling'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { cn } from '@/lib/cn'

export function HeaderBalance() {
  const { data } = useBalance()
  const openTopUp = useTopUpModal()
  if (!data) return null

  const low = data.limits < OPERATION_COST.interview

  return (
    <div className="border-line flex shrink-0 items-center rounded-md border">
      <Link
        to="/app/balance"
        title="Баланс"
        className={cn(
          'hover:bg-glass flex touch-manipulation items-center rounded-md px-2.5 py-1.5 text-sm font-semibold transition-colors sm:rounded-r-none',
          low ? 'text-star' : 'text-ink',
        )}
      >
        <Limits value={data.limits} />
      </Link>
      <button
        type="button"
        onClick={() => openTopUp()}
        aria-label="Пополнить баланс"
        title="Пополнить баланс"
        className="border-line text-indigo hover:bg-glass hover:text-violet hidden touch-manipulation items-center self-stretch rounded-r-md border-l px-2.5 text-base leading-none font-semibold transition-colors sm:flex"
      >
        +
      </button>
    </div>
  )
}
