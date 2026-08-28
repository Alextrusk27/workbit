/** HTTP-слой: тонкая обёртка над fetch. Токены — в HttpOnly-куках, поэтому все
 *  запросы идут с `credentials: 'include'`, вручную заголовок Authorization не ставим.
 *  На 401 (кроме публичных auth-ручек) один раз пробуем silent refresh и повторяем. */

export const BASE_URL: string =
  import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

/** Публичные auth-ручки: для них refresh на 401 не имеет смысла (401 = неверный
 *  или протухший код), а для `/refresh` он ещё и зациклит. */
const NO_REFRESH_PATHS = [
  '/auth/request-code',
  '/auth/verify-code',
  '/auth/refresh',
]

/** Тело ошибки с бэка: `status` — строковое имя enum HttpStatus (напр. "BAD_REQUEST"). */
export interface ApiError {
  timestamp: string
  status: string
  message: string
  errors: string[]
}

export class ApiRequestError extends Error {
  /** HTTP-статус ответа (число). */
  readonly status: number
  /** Разобранное тело ApiError, если пришло. */
  readonly body: ApiError | null

  constructor(status: number, body: ApiError | null) {
    super(body?.message || `Запрос завершился ошибкой (${status})`)
    this.name = 'ApiRequestError'
    this.status = status
    this.body = body
  }
}

let refreshPromise: Promise<boolean> | null = null

/** Single-flight: параллельные 401 дёргают один и тот же `/refresh`. */
function attemptRefresh(): Promise<boolean> {
  refreshPromise ??= fetch(`${BASE_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((r) => r.ok)
    .catch(() => false)
    .finally(() => {
      refreshPromise = null
    })
  return refreshPromise
}

async function toError(res: Response): Promise<ApiRequestError> {
  let body: ApiError | null = null
  try {
    body = (await res.json()) as ApiError
  } catch {
    body = null
  }
  return new ApiRequestError(res.status, body)
}

async function parse<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export interface RequestOptions {
  method?: string
  /** JSON-тело: сериализуется и проставляется Content-Type. */
  body?: unknown
  /** Query-параметры. */
  query?: Record<string, string | number | boolean | undefined>
}

export async function apiFetch<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const { method = 'GET', body, query } = options

  let url = `${BASE_URL}${path}`
  if (query) {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined) qs.set(k, String(v))
    }
    const s = qs.toString()
    if (s) url += `?${s}`
  }

  const init: RequestInit = { method, credentials: 'include' }
  if (body !== undefined) {
    init.headers = { 'Content-Type': 'application/json' }
    init.body = JSON.stringify(body)
  }

  let res = await fetch(url, init)

  if (res.status === 401 && !NO_REFRESH_PATHS.includes(path)) {
    const refreshed = await attemptRefresh()
    if (refreshed) res = await fetch(url, init)
  }

  if (!res.ok) throw await toError(res)
  return parse<T>(res)
}

const REPORT_POLL_ATTEMPTS = 40
const REPORT_POLL_DELAY_MS = 5000

export async function finishWithReportFallback<T>(
  finish: () => Promise<T>,
  fetchReport: () => Promise<T>,
): Promise<T> {
  try {
    return await finish()
  } catch (error) {
    if (error instanceof ApiRequestError) throw error
    for (let attempt = 0; attempt < REPORT_POLL_ATTEMPTS; attempt++) {
      if (attempt > 0) {
        await new Promise((resolve) =>
          setTimeout(resolve, REPORT_POLL_DELAY_MS),
        )
      }
      try {
        return await fetchReport()
      } catch (pollError) {
        if (pollError instanceof ApiRequestError && pollError.status !== 404) {
          throw pollError
        }
      }
    }
    throw error
  }
}

/** Человекочитаемое сообщение для UI: бизнес-ошибку берём с бэка, сетевую — общей фразой. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message
  return 'Не удалось связаться с сервером. Проверь соединение и повтори.'
}

/** Деталь ошибки с бэка (ApiError.errors[0], фолбэк — message) для маппинга в русский текст. */
export function apiErrorDetail(error: unknown): string | null {
  if (error instanceof ApiRequestError) {
    return error.body?.errors?.[0] ?? error.body?.message ?? null
  }
  return null
}
