import { useCallback, useEffect, useRef, useState } from 'react'
import { dictationUrl, STOP_COMMAND, type SttEvent } from './api'
import {
  startMicRecorder,
  UnsupportedBrowserError,
  type MicRecorder,
} from './audio'

export type DictationState = 'idle' | 'starting' | 'listening' | 'stopping'

export interface Dictation {
  state: DictationState
  partial: string
  notice: string | null
  error: string | null
  start: () => void
  stop: () => void
  cancel: () => void
}

const SILENCE_WARNING_MS = 20_000
const SILENCE_STOP_MS = 30_000
const MAX_ANSWER_MS = 4 * 60_000
const TICK_MS = 1000
const NORMAL_CLOSURES = [1000, 1005]

const MIC_ERRORS: Record<string, string> = {
  NotAllowedError:
    'Доступ к микрофону запрещён. Разреши его в настройках браузера.',
  NotFoundError: 'Микрофон не найден. Подключи его и попробуй снова.',
  NotReadableError: 'Микрофон занят другим приложением.',
}

function micErrorMessage(error: unknown): string {
  if (error instanceof UnsupportedBrowserError) {
    return 'Браузер не поддерживает запись с микрофона. Ответь текстом.'
  }
  const name = error instanceof DOMException ? error.name : ''
  return MIC_ERRORS[name] ?? 'Не удалось включить микрофон. Ответь текстом.'
}

export function useDictation(onText: (text: string) => void): Dictation {
  const [state, setState] = useState<DictationState>('idle')
  const [partial, setPartial] = useState('')
  const [notice, setNotice] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const socketRef = useRef<WebSocket | null>(null)
  const recorderRef = useRef<MicRecorder | null>(null)
  const startingRef = useRef(false)
  const generation = useRef(0)
  const pendingFinal = useRef('')
  const startedAt = useRef(0)
  const lastVoice = useRef(0)
  const ticker = useRef<number | null>(null)
  const onTextRef = useRef(onText)
  onTextRef.current = onText

  const stopTicker = useCallback(() => {
    if (ticker.current !== null) {
      clearInterval(ticker.current)
      ticker.current = null
    }
  }, [])

  const releaseMic = useCallback(() => {
    const recorder = recorderRef.current
    recorderRef.current = null
    void recorder?.stop()
  }, [])

  const finish = useCallback(() => {
    generation.current += 1
    startingRef.current = false
    stopTicker()
    releaseMic()
    const socket = socketRef.current
    if (socket?.readyState === WebSocket.OPEN) {
      setState('stopping')
      socket.send(STOP_COMMAND)
    } else {
      socket?.close()
      setState('idle')
    }
  }, [releaseMic, stopTicker])

  const startTicker = useCallback(() => {
    stopTicker()
    ticker.current = window.setInterval(() => {
      const now = Date.now()
      if (now - startedAt.current >= MAX_ANSWER_MS) {
        setNotice('Прошло четыре минуты — запись остановлена.')
        finish()
        return
      }
      const silence = now - lastVoice.current
      if (silence >= SILENCE_STOP_MS) {
        setNotice('Ничего не слышно — запись остановлена.')
        finish()
      } else if (silence >= SILENCE_WARNING_MS) {
        const left = Math.ceil((SILENCE_STOP_MS - silence) / 1000)
        setNotice(`Не слышу тебя. Остановлю запись через ${left} с.`)
      }
    }, TICK_MS)
  }, [finish, stopTicker])

  const start = useCallback(() => {
    if (socketRef.current || startingRef.current) return
    startingRef.current = true
    const gen = generation.current
    setError(null)
    setNotice(null)
    setState('starting')
    pendingFinal.current = ''

    startMicRecorder((pcm) => {
      const socket = socketRef.current
      if (socket?.readyState === WebSocket.OPEN) socket.send(pcm)
    })
      .then((recorder) => {
        if (gen !== generation.current) {
          void recorder.stop()
          return
        }
        startingRef.current = false
        recorderRef.current = recorder
        const socket = new WebSocket(dictationUrl())
        socketRef.current = socket

        socket.onopen = () => {
          startedAt.current = Date.now()
          lastVoice.current = Date.now()
          setState('listening')
          startTicker()
        }

        socket.onmessage = (message: MessageEvent<string>) => {
          const event = JSON.parse(message.data) as SttEvent
          lastVoice.current = Date.now()
          setNotice(null)
          if (event.type === 'PARTIAL') {
            setPartial(event.text)
          } else if (event.type === 'FINAL') {
            pendingFinal.current = event.text
          } else if (event.type === 'REFINEMENT') {
            pendingFinal.current = ''
            setPartial('')
            onTextRef.current(event.text)
          } else {
            setError('Распознавание прервалось. Наговоренное сохранено.')
          }
        }

        socket.onclose = (event) => {
          socketRef.current = null
          stopTicker()
          releaseMic()
          if (pendingFinal.current) {
            onTextRef.current(pendingFinal.current)
            pendingFinal.current = ''
          }
          if (!NORMAL_CLOSURES.includes(event.code)) {
            setError('Связь с сервером прервалась. Наговоренное сохранено.')
          }
          setPartial('')
          setState('idle')
        }
      })
      .catch((micError: unknown) => {
        if (gen !== generation.current) return
        startingRef.current = false
        setState('idle')
        setError(micErrorMessage(micError))
      })
  }, [releaseMic, startTicker, stopTicker])

  const cancel = useCallback(() => {
    generation.current += 1
    startingRef.current = false
    stopTicker()
    releaseMic()
    pendingFinal.current = ''
    const socket = socketRef.current
    socketRef.current = null
    if (socket) {
      socket.onmessage = null
      socket.onclose = null
      socket.onerror = null
      socket.close()
    }
    setPartial('')
    setNotice(null)
    setError(null)
    setState('idle')
  }, [releaseMic, stopTicker])

  useEffect(
    () => () => {
      generation.current += 1
      if (ticker.current !== null) clearInterval(ticker.current)
      const recorder = recorderRef.current
      recorderRef.current = null
      void recorder?.stop()
      const socket = socketRef.current
      socketRef.current = null
      if (socket) {
        socket.onmessage = null
        socket.onclose = null
        socket.onerror = null
        socket.close()
      }
    },
    [],
  )

  return { state, partial, notice, error, start, stop: finish, cancel }
}
