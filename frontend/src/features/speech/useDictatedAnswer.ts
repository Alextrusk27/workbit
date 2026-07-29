import { useCallback, useEffect, useState } from 'react'
import { useDictation, type Dictation } from './useDictation'

export interface DictatedAnswer {
  text: string
  setText: (value: string) => void
  dictation: Dictation
  recording: boolean
  busy: boolean
  canSend: boolean
  send: () => void
  toggleMic: () => void
}

export function useDictatedAnswer(
  onSubmit: (text: string) => void,
  { disabled, pending }: { disabled: boolean; pending: boolean },
): DictatedAnswer {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)

  const dictation = useDictation((chunk) =>
    setText((prev) => (prev ? `${prev} ${chunk}` : chunk)),
  )

  const recording =
    dictation.state === 'starting' || dictation.state === 'listening'
  const dictating = dictation.state !== 'idle'

  const submit = useCallback(
    (value: string) => {
      onSubmit(value)
      setText('')
    },
    [onSubmit],
  )

  useEffect(() => {
    if (!busy || dictation.state !== 'idle') return
    setBusy(false)
    const value = text.trim()
    if (value) submit(value)
  }, [busy, dictation.state, text, submit])

  const send = useCallback(() => {
    if (disabled || pending || busy) return
    if (dictating) {
      setBusy(true)
      dictation.stop()
      return
    }
    const value = text.trim()
    if (!value) return
    dictation.cancel()
    submit(value)
  }, [disabled, pending, busy, dictating, dictation, text, submit])

  const toggleMic = useCallback(() => {
    if (recording) dictation.stop()
    else dictation.start()
  }, [recording, dictation])

  const canSend =
    !disabled && !pending && !busy && (text.trim() !== '' || dictating)

  return { text, setText, dictation, recording, busy, canSend, send, toggleMic }
}
