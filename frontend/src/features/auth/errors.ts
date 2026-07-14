import { ApiRequestError, getErrorMessage } from '@/lib/api'

/** Детали auth-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const AUTH_DETAIL = {
  INVALID_CREDENTIALS: 'Invalid credentials',
  EMAIL_NOT_VERIFIED: 'Email not verified',
  EMAIL_REGISTERED_UNVERIFIED: 'Email registered but not verified',
  EMAIL_IN_USE: 'Email already in use',
} as const

function authDetail(error: unknown): string | null {
  if (error instanceof ApiRequestError) {
    return error.body?.errors?.[0] ?? error.body?.message ?? null
  }
  return null
}

export function isEmailNotVerified(error: unknown): boolean {
  return authDetail(error) === AUTH_DETAIL.EMAIL_NOT_VERIFIED
}

export function isEmailRegisteredUnverified(error: unknown): boolean {
  return authDetail(error) === AUTH_DETAIL.EMAIL_REGISTERED_UNVERIFIED
}

export function isEmailInUse(error: unknown): boolean {
  return authDetail(error) === AUTH_DETAIL.EMAIL_IN_USE
}

const RU_MESSAGE: Record<string, string> = {
  [AUTH_DETAIL.INVALID_CREDENTIALS]: 'Неверный email или пароль.',
  [AUTH_DETAIL.EMAIL_NOT_VERIFIED]:
    'Email не подтверждён. Подтвердите почту, чтобы войти.',
  [AUTH_DETAIL.EMAIL_REGISTERED_UNVERIFIED]:
    'Этот email уже зарегистрирован, но не подтверждён. Проверьте почту или отправьте письмо заново.',
  [AUTH_DETAIL.EMAIL_IN_USE]:
    'Этот email уже зарегистрирован. Войдите или восстановите пароль.',
}

/** Русское сообщение auth-ошибки: известные случаи маппим, иначе — общий текст. */
export function authErrorMessage(error: unknown): string {
  const detail = authDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
