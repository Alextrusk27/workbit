import { Link } from 'react-router-dom'
import { Limits } from '@/components/ui/LimitIcon'
import { Skeleton } from '@/components/ui/Skeleton'
import { OPERATION_COST } from '@/content/limits'
import { useBalance } from '@/features/billing/useBilling'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { cn } from '@/lib/cn'

export function HeaderBalance() {
  const { data, isLoading } = useBalance()
  const openTopUp = useTopUpModal()
  if (isLoading)
    return (
      <Skeleton className="h-11 w-14 shrink-0 rounded-md sm:h-[38px] sm:w-[104px]" />
    )
  if (!data) return null

  const low = data.limits < OPERATION_COST.interview

  return (
    <div className="border-line flex shrink-0 items-center rounded-lg border">
      <Link
        to="/app/balance"
        title="Баланс"
        className={cn(
          'hover:bg-glass flex min-h-11 touch-manipulation items-center rounded-lg px-2.5 py-1.5 text-sm font-semibold transition-colors sm:min-h-9 sm:rounded-r-none sm:px-3 sm:text-[15px]',
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
        className="border-line text-indigo hover:bg-glass hover:text-violet hidden touch-manipulation items-center self-stretch rounded-r-lg border-l px-3 text-lg leading-none font-semibold transition-colors sm:flex"
      >
        +
      </button>
    </div>
  )
}
