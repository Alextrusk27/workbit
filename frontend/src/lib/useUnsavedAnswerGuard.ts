import { useEffect } from 'react'

/** Предупреждение браузера при закрытии вкладки с непустым черновиком ответа. */
export function useUnsavedAnswerGuard(text: string) {
  useEffect(() => {
    if (!text.trim()) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [text])
}
