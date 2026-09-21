import { OPERATION_COST } from '@/content/limits'
import { apiErrorDetail, getErrorMessage } from '@/lib/api'

/** Детали training-ошибок с бэка (ApiError.errors[0]). Стабильный контракт для UI. */
const TRAINING_DETAIL = {
  SKILL_NOT_RECOGNIZED: 'Skill not recognized',
  PROFESSION_NOT_RECOGNIZED: 'Profession not recognized',
  NO_NEW_QUESTIONS: 'No new questions available',
  QUESTION_LIMIT_REACHED: 'Question limit reached',
  NOT_ENOUGH_LIMITS: 'Not enough limits',
  PURCHASE_REQUIRED: 'Purchase required',
} as const

export type TrainingOperation = 'training' | 'more' | 'reference'

const OPERATION_NAME: Record<TrainingOperation, string> = {
  training: 'тренировка',
  more: 'добор вопросов',
  reference: 'эталонный ответ',
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
  [TRAINING_DETAIL.PURCHASE_REQUIRED]:
    'Эталонные ответы доступны после первого пополнения баланса.',
}

/** Русское сообщение training-ошибки: известные случаи маппим, иначе — общий текст.
 *  `operation` подставляет цену в текст 402 — стоит операция по-разному. */
export function trainingErrorMessage(
  error: unknown,
  operation: TrainingOperation = 'training',
): string {
  const detail = apiErrorDetail(error)
  if (detail === TRAINING_DETAIL.NOT_ENOUGH_LIMITS)
    return `Не хватает лимитов: ${OPERATION_NAME[operation]} стоит ${OPERATION_COST[operation]}. Пополни баланс на странице тарифов.`
  if (detail && RU_MESSAGE[detail]) return RU_MESSAGE[detail]
  return getErrorMessage(error)
}
