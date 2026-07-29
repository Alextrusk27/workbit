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
          : 'border-violet bg-violet hover:bg-violet/85 text-white shadow-[0_3px_12px_rgba(139,92,246,0.45)]',
      )}
    >
      <IconMic className="size-4" />
    </button>
  )
}
