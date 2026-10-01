import { apiErrorDetail, getErrorMessage } from '@/lib/api'

/** Детали resume-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const RESUME_DETAIL = {
  UNSUPPORTED_FORMAT: 'Unsupported format',
  LEGACY_FORMAT: 'Legacy format',
  FILE_TOO_LARGE: 'File too large',
  RESUME_LIMIT_REACHED: 'Resume limit reached',
  TOO_MANY_REQUESTS: 'Too many requests',
  RESUME_NOT_FOUND: 'Resume not found',
  RESUME_FILE_MISSING: 'Resume file missing',
} as const

export const FILE_TOO_LARGE_MESSAGE = 'Файл больше 5 МБ.'

export const UNSUPPORTED_FORMAT_MESSAGE =
  'Поддерживаются только PDF, DOCX и TXT.'

const RU_MESSAGE: Record<string, string> = {
  [RESUME_DETAIL.UNSUPPORTED_FORMAT]: UNSUPPORTED_FORMAT_MESSAGE,
  [RESUME_DETAIL.LEGACY_FORMAT]:
    'Старые файлы Word, RTF и документы Word под паролем не поддерживаются. Сохрани файл как DOCX без пароля или PDF.',
  [RESUME_DETAIL.FILE_TOO_LARGE]: FILE_TOO_LARGE_MESSAGE,
  [RESUME_DETAIL.RESUME_LIMIT_REACHED]:
    'У тебя уже 3 резюме. Удали одно, чтобы загрузить новое.',
  [RESUME_DETAIL.TOO_MANY_REQUESTS]:
    'Слишком много загрузок за сутки. Попробуй завтра.',
  [RESUME_DETAIL.RESUME_NOT_FOUND]: 'Резюме уже удалено.',
  [RESUME_DETAIL.RESUME_FILE_MISSING]:
    'Исходный файл недоступен. Загрузи резюме заново.',
}

export function resumeErrorMessage(error: unknown): string {
  const detail = apiErrorDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
