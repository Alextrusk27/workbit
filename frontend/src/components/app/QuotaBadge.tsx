import { useQuota } from '@/features/billing/useBilling'

/** Суммарный остаток (подписка + пакеты); при загрузке или ошибке не рендерится. */
export function QuotaBadge({ kind }: { kind: 'interview' | 'training' }) {
  const { data } = useQuota()
  if (!data) return null
  const left =
    kind === 'interview'
      ? data.planInterviewsLeft + data.packInterviewsLeft
      : data.planTrainingsLeft + data.packTrainingsLeft
  return (
    <span className="text-dim text-[13px] whitespace-nowrap tabular-nums">
      Осталось: {left}
    </span>
  )
}
