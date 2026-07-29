import { IconMic } from '@/components/marketing/icons'
import { cn } from '@/lib/cn'

export function MicButton({
  recording,
  disabled,
  onClick,
}: {
  recording: boolean
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={recording ? 'Остановить запись' : 'Ответить голосом'}
      aria-pressed={recording}
      className={cn(
        'grid size-9 shrink-0 place-items-center rounded-full border transition-colors disabled:opacity-45',
        recording
          ? 'border-danger/40 bg-danger/12 text-danger mic-pulse'
          : 'border-line bg-glass text-muted hover:bg-glass-hover hover:text-ink',
      )}
    >
      <IconMic className="size-4" />
    </button>
  )
}
