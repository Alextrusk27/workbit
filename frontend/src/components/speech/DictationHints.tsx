import type { Dictation } from '@/features/speech/useDictation'

export function DictationHints({ dictation }: { dictation: Dictation }) {
  return (
    <>
      {dictation.partial && (
        <p className="text-dim mt-1.5 text-[12.5px]">{dictation.partial}</p>
      )}
      {dictation.notice && (
        <p role="status" className="text-dim mt-1.5 text-[12.5px]">
          {dictation.notice}
        </p>
      )}
      {dictation.state === 'stopping' && (
        <p className="text-dim mt-1.5 text-[12.5px]">Расшифровываю…</p>
      )}
      {dictation.error && (
        <p role="status" className="text-danger mt-1.5 text-[12.5px]">
          {dictation.error}
        </p>
      )}
    </>
  )
}
