/** HTTP-слой: тонкая обёртка над fetch. Токены — в HttpOnly-куках, поэтому все
 *  запросы идут с `credentials: 'include'`, вручную заголовок Authorization не ставим.
 *  На 401 (кроме публичных auth-ручек) один раз пробуем silent refresh и повторяем. */

const BASE_URL: string =
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

/** Человекочитаемое сообщение для UI: бизнес-ошибку берём с бэка, сетевую — общей фразой. */
export function getErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) return error.message
  return 'Не удалось связаться с сервером. Проверьте соединение и повторите.'
}
