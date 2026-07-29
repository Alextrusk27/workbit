import { cn } from '@/lib/cn'

/** Метка статуса сессии: завершённые — зелёные, остальные — индиго. */
export function StatusTag({ label, done }: { label: string; done: boolean }) {
  return (
    <span
      className={cn(
        'rounded-sm px-2.5 py-[3px] text-xs font-semibold',
        done ? 'bg-ok/12 text-ok' : 'bg-indigo/12 text-indigo',
      )}
    >
      {label}
    </span>
  )
}
