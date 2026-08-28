/** Ссылка на вакансию, вставленная в герое `/ai-interview` до авторизации.
 *  Именно `localStorage`: код входа могут открыть в новой вкладке, куда
 *  `sessionStorage` не переезжает. Чтение одноразовое — запись стирается. */
const PENDING_VACANCY_URL_KEY = 'workbit:pending-vacancy-url'
const TTL_MS = 30 * 60_000

export function savePendingVacancyUrl(url: string): void {
  try {
    localStorage.setItem(
      PENDING_VACANCY_URL_KEY,
      JSON.stringify({ url, savedAt: Date.now() }),
    )
  } catch {
    // хранилище запрещено — перенос просто не сработает
  }
}

export function takePendingVacancyUrl(): string {
  try {
    const raw = localStorage.getItem(PENDING_VACANCY_URL_KEY)
    if (!raw) return ''
    localStorage.removeItem(PENDING_VACANCY_URL_KEY)
    const { url, savedAt } = JSON.parse(raw) as {
      url?: unknown
      savedAt?: unknown
    }
    if (typeof url !== 'string' || typeof savedAt !== 'number') return ''
    return Date.now() - savedAt < TTL_MS ? url : ''
  } catch {
    return ''
  }
}
