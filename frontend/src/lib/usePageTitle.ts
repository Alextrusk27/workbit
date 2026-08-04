import { useEffect } from 'react'

const BASE_TITLE = 'Workbit — тренажёр собеседований с AI'

/** Заголовок вкладки: «<title> — Workbit»; без аргумента — базовый. */
export function usePageTitle(title?: string) {
  useEffect(() => {
    document.title = title ? `${title} — Workbit` : BASE_TITLE
  }, [title])
}
