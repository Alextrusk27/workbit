import { apiErrorDetail, getErrorMessage } from '@/lib/api'

/** Детали resume-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const RESUME_DETAIL = {
  UNSUPPORTED_FORMAT: 'Unsupported format',
} as const

const RU_MESSAGE: Record<string, string> = {
  [RESUME_DETAIL.UNSUPPORTED_FORMAT]: 'Поддерживаются только PDF.',
}

export function resumeErrorMessage(error: unknown): string {
  const detail = apiErrorDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
