import { OPERATION_COST } from '@/content/limits'
import { useBalance } from '@/features/billing/useBilling'
import { cn } from '@/lib/cn'
import { limitsWord } from '@/lib/plural'

/** Баланс лимитов; при загрузке или ошибке не рендерится. */
export function QuotaBadge() {
  const { data } = useBalance()
  if (!data) return null
  const low = data.limits < OPERATION_COST.interview
  return (
    <span
      className={cn(
        'text-[13px] whitespace-nowrap tabular-nums',
        low ? 'text-star font-semibold' : 'text-dim',
      )}
    >
      {data.limits} {limitsWord(data.limits)}
    </span>
  )
}
