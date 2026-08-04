import { useEffect } from 'react'

const BASE_TITLE = 'Workbit — AI-интервью'

/** Заголовок вкладки: «<title> — Workbit»; без аргумента — базовый. */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} — Workbit` : BASE_TITLE
  }, [title])
}
