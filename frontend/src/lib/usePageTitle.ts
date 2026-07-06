import { useEffect } from 'react'

const BASE_TITLE = 'workbit — AI-интервью'

/** Заголовок вкладки: «<title> — workbit»; без аргумента — базовый. */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} — workbit` : BASE_TITLE
  }, [title])
}
