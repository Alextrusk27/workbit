import { ApiRequestError, getErrorMessage } from '@/lib/api'
import { CaptchaError } from './captcha'

/** Детали auth-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const AUTH_DETAIL = {
  INVALID_CODE: 'Invalid code',
  CODE_EXPIRED: 'Code has expired',
  TOO_MANY_ATTEMPTS: 'Too many attempts',
  TOO_MANY_REQUESTS: 'Too many requests',
  CAPTCHA_FAILED: 'Captcha validation failed',
} as const

function authDetail(error: unknown): string | null {
  if (error instanceof ApiRequestError) {
    return error.body?.errors?.[0] ?? error.body?.message ?? null
  }
  return null
}

const RU_MESSAGE: Record<string, string> = {
  [AUTH_DETAIL.INVALID_CODE]:
    'Неверный код. Проверьте письмо и повторите ввод.',
  [AUTH_DETAIL.CODE_EXPIRED]: 'Срок действия кода истёк. Запросите код заново.',
  [AUTH_DETAIL.TOO_MANY_ATTEMPTS]:
    'Слишком много неверных попыток. Запросите новый код.',
  [AUTH_DETAIL.TOO_MANY_REQUESTS]:
    'Слишком много запросов. Подождите немного и попробуйте снова.',
  [AUTH_DETAIL.CAPTCHA_FAILED]:
    'Не удалось подтвердить, что вы не робот. Попробуйте ещё раз.',
}

const CAPTCHA_MESSAGE: Record<CaptchaError['kind'], string> = {
  cancelled: 'Проверка «я не робот» прервана. Попробуйте ещё раз.',
  unavailable:
    'Не удалось загрузить проверку от роботов. Отключите блокировщик рекламы или попробуйте позже.',
}

/** Русское сообщение auth-ошибки: известные случаи маппим, иначе — общий текст. */
export function authErrorMessage(error: unknown): string {
  if (error instanceof CaptchaError) return CAPTCHA_MESSAGE[error.kind]
  const detail = authDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
