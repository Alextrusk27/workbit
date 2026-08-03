import { ApiRequestError, getErrorMessage } from '@/lib/api'

/** Детали training-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const TRAINING_DETAIL = {
  SKILL_NOT_RECOGNIZED: 'Skill not recognized',
  PROFESSION_NOT_RECOGNIZED: 'Profession not recognized',
} as const

function trainingDetail(error: unknown): string | null {
  if (error instanceof ApiRequestError) {
    return error.body?.errors?.[0] ?? error.body?.message ?? null
  }
  return null
}

const RU_MESSAGE: Record<string, string> = {
  [TRAINING_DETAIL.SKILL_NOT_RECOGNIZED]:
    'Не получилось распознать навык. Уточните формулировку или выберите вариант из подсказок.',
  [TRAINING_DETAIL.PROFESSION_NOT_RECOGNIZED]:
    'Не получилось распознать профессию. Уточните формулировку или выберите вариант из подсказок.',
}

/** Русское сообщение training-ошибки: известные случаи маппим, иначе — общий текст. */
export function trainingErrorMessage(error: unknown): string {
  const detail = trainingDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
