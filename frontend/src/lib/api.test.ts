import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiRequestError,
  apiFetch,
  finishWithReportFallback,
  getErrorMessage,
} from './api'

const fetchMock = vi.fn()

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status })

const apiError = (status: number, detail: string) =>
  json(status, {
    timestamp: '2026-08-21T00:00:00Z',
    status: 'ERROR',
    message: detail,
    errors: [detail],
  })

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
  vi.useRealTimers()
})

describe('apiFetch', () => {
  it('разбирает JSON успешного ответа', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { id: 7 }))

    await expect(apiFetch('/auth/me')).resolves.toEqual({ id: 7 })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toContain('/auth/me')
    expect(init.credentials).toBe('include')
  })

  it('204 отдаёт undefined', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(apiFetch('/x')).resolves.toBeUndefined()
  })

  it('сериализует query и отбрасывает undefined', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {}))

    await apiFetch('/vacancies/status', {
      query: { url: 'https://hh.ru/vacancy/1', skip: undefined },
    })
    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain('?url=https%3A%2F%2Fhh.ru%2Fvacancy%2F1')
    expect(url).not.toContain('skip')
  })

  it('шлёт JSON-тело с Content-Type', async () => {
    fetchMock.mockResolvedValueOnce(json(200, {}))

    await apiFetch('/training/normalize', {
      method: 'POST',
      body: { skill: 'Docker' },
    })
    const [, init] = fetchMock.mock.calls[0]
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(init.body).toBe('{"skill":"Docker"}')
  })

  it('на 401 делает refresh и повторяет запрос', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, {}))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json(200, { ok: true }))

    await expect(apiFetch('/billing/quota')).resolves.toEqual({ ok: true })
    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fetchMock.mock.calls[1][0]).toContain('/auth/refresh')
  })

  it('после неудачного refresh бросает исходный 401', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, {}))
      .mockResolvedValueOnce(json(401, {}))

    await expect(apiFetch('/billing/quota')).rejects.toMatchObject({
      status: 401,
    })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('не делает refresh для публичных auth-ручек', async () => {
    fetchMock.mockResolvedValueOnce(apiError(401, 'Invalid code'))

    await expect(
      apiFetch('/auth/verify-code', { method: 'POST', body: {} }),
    ).rejects.toMatchObject({ status: 401 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('параллельные 401 делят один refresh', async () => {
    const seen = new Set<string>()
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/auth/refresh')) {
        return Promise.resolve(new Response(null, { status: 204 }))
      }
      if (!seen.has(url)) {
        seen.add(url)
        return Promise.resolve(json(401, {}))
      }
      return Promise.resolve(json(200, {}))
    })

    await Promise.all([apiFetch('/a'), apiFetch('/b')])
    const refreshCalls = fetchMock.mock.calls.filter(([url]) =>
      String(url).includes('/auth/refresh'),
    )
    expect(refreshCalls).toHaveLength(1)
  })

  it('собирает ApiRequestError из тела ошибки', async () => {
    fetchMock.mockResolvedValueOnce(apiError(422, 'Skill not recognized'))

    const error = await apiFetch('/training/sessions', {
      method: 'POST',
    }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiRequestError)
    const apiErr = error as ApiRequestError
    expect(apiErr.status).toBe(422)
    expect(apiErr.message).toBe('Skill not recognized')
    expect(apiErr.body?.errors).toEqual(['Skill not recognized'])
  })

  it('ошибка без JSON-тела даёт запасное сообщение', async () => {
    fetchMock.mockResolvedValueOnce(new Response('oops', { status: 500 }))

    await expect(apiFetch('/x')).rejects.toMatchObject({
      status: 500,
      body: null,
      message: 'Запрос завершился ошибкой (500)',
    })
  })
})

describe('finishWithReportFallback', () => {
  it('успешный finish не поллит отчёт', async () => {
    const fetchReport = vi.fn()

    await expect(
      finishWithReportFallback(() => Promise.resolve('report'), fetchReport),
    ).resolves.toBe('report')
    expect(fetchReport).not.toHaveBeenCalled()
  })

  it('бизнес-ошибка finish пробрасывается сразу', async () => {
    const quota = new ApiRequestError(402, null)
    const fetchReport = vi.fn()

    await expect(
      finishWithReportFallback(() => Promise.reject(quota), fetchReport),
    ).rejects.toBe(quota)
    expect(fetchReport).not.toHaveBeenCalled()
  })

  it('сетевой обрыв finish добирается поллингом отчёта', async () => {
    vi.useFakeTimers()
    const fetchReport = vi
      .fn()
      .mockRejectedValueOnce(new ApiRequestError(404, null))
      .mockRejectedValueOnce(new ApiRequestError(404, null))
      .mockResolvedValueOnce('report')

    const promise = finishWithReportFallback(
      () => Promise.reject(new TypeError('fetch failed')),
      fetchReport,
    )
    const assertion = expect(promise).resolves.toBe('report')
    await vi.advanceTimersByTimeAsync(10_000)
    await assertion
    expect(fetchReport).toHaveBeenCalledTimes(3)
  })

  it('не-404 при поллинге пробрасывается', async () => {
    const serverError = new ApiRequestError(500, null)

    await expect(
      finishWithReportFallback(
        () => Promise.reject(new TypeError('fetch failed')),
        () => Promise.reject(serverError),
      ),
    ).rejects.toBe(serverError)
  })

  it('после исчерпания попыток бросает исходную ошибку', async () => {
    vi.useFakeTimers()
    const network = new TypeError('fetch failed')
    const fetchReport = vi.fn(() =>
      Promise.reject(new ApiRequestError(404, null)),
    )

    const promise = finishWithReportFallback(
      () => Promise.reject(network),
      fetchReport,
    )
    const assertion = expect(promise).rejects.toBe(network)
    await vi.advanceTimersByTimeAsync(200_000)
    await assertion
    expect(fetchReport).toHaveBeenCalledTimes(40)
  })
})

describe('getErrorMessage', () => {
  it('бизнес-ошибку берёт с бэка', () => {
    const error = new ApiRequestError(409, {
      timestamp: '2026-08-21T00:00:00Z',
      status: 'CONFLICT',
      message: 'No new questions available',
      errors: [],
    })

    expect(getErrorMessage(error)).toBe('No new questions available')
  })

  it('сетевую ошибку заменяет общей фразой', () => {
    expect(getErrorMessage(new TypeError('fetch failed'))).toBe(
      'Не удалось связаться с сервером. Проверьте соединение и повторите.',
    )
  })
})
