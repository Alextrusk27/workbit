const SCRIPT_URL = 'https://smartcaptcha.yandexcloud.net/captcha.js'
const CHALLENGE_HIDDEN_GRACE_MS = 500
const TOKEN_TIMEOUT_MS = 90_000

const sitekey = import.meta.env.VITE_SMARTCAPTCHA_SITEKEY as string | undefined

/** Капча настроена. Щит SmartCaptcha скрыт (`hideShield`), поэтому уведомление
 *  об обработке данных показывает сама форма входа. */
export const captchaEnabled = Boolean(sitekey)

interface SmartCaptcha {
  render: (
    container: HTMLElement,
    options: {
      sitekey: string
      invisible?: boolean
      hideShield?: boolean
      callback?: (token: string) => void
    },
  ) => string
  execute: (widgetId: string) => void
  reset: (widgetId: string) => void
  subscribe: (
    widgetId: string,
    event: 'challenge-hidden' | 'network-error' | 'token-expired',
    callback: () => void,
  ) => () => void
}

declare global {
  interface Window {
    smartCaptcha?: SmartCaptcha
  }
}

/** Ошибка получения токена капчи: `cancelled` — пользователь закрыл задание,
 *  `unavailable` — скрипт не загрузился или сеть до капчи не работает. */
export class CaptchaError extends Error {
  readonly kind: 'cancelled' | 'unavailable'

  constructor(kind: 'cancelled' | 'unavailable') {
    super(`Captcha ${kind}`)
    this.name = 'CaptchaError'
    this.kind = kind
  }
}

let scriptPromise: Promise<SmartCaptcha> | null = null
let widgetId: string | null = null
let settleToken: ((result: PromiseSettledResult<string>) => void) | null = null

function loadScript(): Promise<SmartCaptcha> {
  if (window.smartCaptcha) return Promise.resolve(window.smartCaptcha)
  scriptPromise ??= new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.defer = true
    script.onload = () => {
      if (window.smartCaptcha) resolve(window.smartCaptcha)
      else reject(new CaptchaError('unavailable'))
    }
    script.onerror = () => {
      scriptPromise = null
      reject(new CaptchaError('unavailable'))
    }
    document.head.appendChild(script)
  })
  return scriptPromise
}

function settle(result: PromiseSettledResult<string>) {
  const current = settleToken
  settleToken = null
  current?.(result)
}

function ensureWidget(captcha: SmartCaptcha, key: string): string {
  if (widgetId !== null) return widgetId
  const container = document.createElement('div')
  document.body.appendChild(container)
  widgetId = captcha.render(container, {
    sitekey: key,
    invisible: true,
    hideShield: true,
    callback: (token) => settle({ status: 'fulfilled', value: token }),
  })
  captcha.subscribe(widgetId, 'challenge-hidden', () => {
    const pending = settleToken
    setTimeout(() => {
      if (pending !== null && settleToken === pending) {
        settle({ status: 'rejected', reason: new CaptchaError('cancelled') })
      }
    }, CHALLENGE_HIDDEN_GRACE_MS)
  })
  captcha.subscribe(widgetId, 'network-error', () =>
    settle({ status: 'rejected', reason: new CaptchaError('unavailable') }),
  )
  captcha.subscribe(widgetId, 'token-expired', () =>
    settle({ status: 'rejected', reason: new CaptchaError('unavailable') }),
  )
  return widgetId
}

/** Токен SmartCaptcha для `/auth/request-code`: невидимая проверка, задание
 *  показывается только подозрительным клиентам. Токен одноразовый — получать
 *  перед каждой отправкой. Без `VITE_SMARTCAPTCHA_SITEKEY` (dev) — `undefined`. */
export async function getCaptchaToken(): Promise<string | undefined> {
  if (!sitekey) return undefined
  let captcha: SmartCaptcha
  try {
    captcha = await loadScript()
  } catch {
    throw new CaptchaError('unavailable')
  }
  const id = ensureWidget(captcha, sitekey)
  try {
    return await new Promise<string>((resolve, reject) => {
      settle({ status: 'rejected', reason: new CaptchaError('cancelled') })
      const timer = window.setTimeout(
        () =>
          settle({
            status: 'rejected',
            reason: new CaptchaError('unavailable'),
          }),
        TOKEN_TIMEOUT_MS,
      )
      settleToken = (result) => {
        clearTimeout(timer)
        if (result.status === 'fulfilled') resolve(result.value)
        else reject(result.reason as Error)
      }
      captcha.execute(id)
    })
  } finally {
    captcha.reset(id)
  }
}
