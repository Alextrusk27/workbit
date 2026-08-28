import { ApiRequestError, getErrorMessage } from '@/lib/api'

/** Детали training-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const TRAINING_DETAIL = {
  SKILL_NOT_RECOGNIZED: 'Skill not recognized',
  PROFESSION_NOT_RECOGNIZED: 'Profession not recognized',
  NO_NEW_QUESTIONS: 'No new questions available',
  QUESTION_LIMIT_REACHED: 'Question limit reached',
  QUOTA_EXHAUSTED: 'Training quota exhausted',
  PAID_PLAN_REQUIRED: 'Paid plan required',
} as const

function trainingDetail(error: unknown): string | null {
  if (error instanceof ApiRequestError) {
    return error.body?.errors?.[0] ?? error.body?.message ?? null
  }
  return null
}

const RU_MESSAGE: Record<string, string> = {
  [TRAINING_DETAIL.SKILL_NOT_RECOGNIZED]:
    'Не получилось распознать навык. Уточни формулировку или выбери вариант из подсказок.',
  [TRAINING_DETAIL.PROFESSION_NOT_RECOGNIZED]:
    'Не получилось распознать профессию. Уточни формулировку или выбери вариант из подсказок.',
  [TRAINING_DETAIL.NO_NEW_QUESTIONS]:
    'Новых вопросов этого уровня по навыку не нашлось — всё, что можно спросить, ты уже прошёл. Заверши тренировку и получи разбор.',
  [TRAINING_DETAIL.QUESTION_LIMIT_REACHED]:
    'Достигнут потолок вопросов в одной тренировке. Заверши её и получи разбор.',
  [TRAINING_DETAIL.QUOTA_EXHAUSTED]:
    'Тренировки на твоём тарифе закончились. Обнови или продли тариф на странице тарифов.',
  [TRAINING_DETAIL.PAID_PLAN_REQUIRED]:
    'Добор вопросов доступен на тарифах Про и Макс.',
}

/** Русское сообщение training-ошибки: известные случаи маппим, иначе — общий текст. */
export function trainingErrorMessage(error: unknown): string {
  const detail = trainingDetail(error)
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
