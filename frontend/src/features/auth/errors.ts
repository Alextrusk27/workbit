import { apiErrorDetail, getErrorMessage } from '@/lib/api'
import { CaptchaError } from './captcha'

/** Детали auth-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const AUTH_DETAIL = {
  INVALID_CODE: 'Invalid code',
  CODE_EXPIRED: 'Code has expired',
  TOO_MANY_ATTEMPTS: 'Too many attempts',
  TOO_MANY_REQUESTS: 'Too many requests',
  CAPTCHA_FAILED: 'Captcha validation failed',
} as const

const RU_MESSAGE: Record<string, string> = {
  [AUTH_DETAIL.INVALID_CODE]: 'Неверный код. Проверь письмо и повтори ввод.',
  [AUTH_DETAIL.CODE_EXPIRED]: 'Срок действия кода истёк. Запроси код заново.',
  [AUTH_DETAIL.TOO_MANY_ATTEMPTS]:
    'Слишком много неверных попыток. Запроси новый код.',
  [AUTH_DETAIL.TOO_MANY_REQUESTS]:
    'Слишком много запросов. Подожди немного и попробуй снова.',
  [AUTH_DETAIL.CAPTCHA_FAILED]:
    'Не удалось подтвердить, что ты не робот. Попробуй ещё раз.',
}

const CAPTCHA_MESSAGE: Record<CaptchaError['kind'], string> = {
  cancelled: 'Проверка «я не робот» прервана. Попробуй ещё раз.',
  unavailable:
    'Не удалось загрузить проверку от роботов. Отключи блокировщик рекламы или попробуй позже.',
}

/** Русское сообщение auth-ошибки: известные случаи маппим, иначе — общий текст. */
export function authErrorMessage(error: unknown): string {
  if (error instanceof CaptchaError) return CAPTCHA_MESSAGE[error.kind]
  const detail = apiErrorDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
