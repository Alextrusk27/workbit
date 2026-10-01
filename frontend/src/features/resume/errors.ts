import { apiErrorDetail, getErrorMessage } from '@/lib/api'

/** Детали resume-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const RESUME_DETAIL = {
  UNSUPPORTED_FORMAT: 'Unsupported format',
  LEGACY_FORMAT: 'Legacy format',
} as const

export const UNSUPPORTED_FORMAT_MESSAGE =
  'Поддерживаются только PDF, DOCX и TXT.'

const RU_MESSAGE: Record<string, string> = {
  [RESUME_DETAIL.UNSUPPORTED_FORMAT]: UNSUPPORTED_FORMAT_MESSAGE,
  [RESUME_DETAIL.LEGACY_FORMAT]:
    'Старые файлы Word, RTF и документы Word под паролем не поддерживаются. Сохрани файл как DOCX без пароля или PDF.',
}

export function resumeErrorMessage(error: unknown): string {
  const detail = apiErrorDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
