import type { Dictation } from '@/features/speech/useDictation'
import { cn } from '@/lib/cn'

function hintText(dictation: Dictation): string {
  if (dictation.error) return dictation.error
  if (dictation.notice) return dictation.notice
  if (dictation.state === 'stopping') return 'Расшифровываю…'
  if (dictation.partial) return dictation.partial
  if (dictation.state === 'listening') return 'Слушаю…'
  return 'Можно голосом — нажми микрофон'
}

/** Одна строка состояния диктовки под полем ответа: высота фиксирована,
    состояния сменяют друг друга, композер не прыгает. */
export function DictationHints({ dictation }: { dictation: Dictation }) {
  const announced = Boolean(dictation.error || dictation.notice)
  return (
    <p
      role="status"
      className="mt-1.5 flex h-4.5 items-center gap-1.5 overflow-hidden text-[12.5px]"
    >
      {dictation.state === 'listening' && (
        <span className="bg-danger dot-blink size-1.5 shrink-0 rounded-full" />
      )}
      {dictation.state === 'stopping' && (
        <span className="border-indigo/30 border-t-indigo dot-spin size-2.5 shrink-0 rounded-full border-[1.5px]" />
      )}
      <span
        aria-hidden={!announced}
        className={cn('truncate', dictation.error ? 'text-danger' : 'text-dim')}
      >
        {hintText(dictation)}
      </span>
    </p>
  )
}
