import type { InterviewSession, OfferProbability, SessionStatus } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Не начато',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}

/** Заголовок интервью — название вакансии. */
export function sessionHeadline(session: {
  vacancyName: InterviewSession['vacancyName']
}): string {
  return session.vacancyName
}

/** Подпись под заголовком — работодатель. */
export function sessionSubtitle(session: {
  employer: InterviewSession['employer']
}): string {
  return session.employer || 'Работодатель не указан'
}

/** Код уровня тренировки по требуемому опыту вакансии: строки hh.ru разложены
 *  как в грейдовом роутинге бэка, «нет опыта» и нераспознанное — начальный. */
export type TrainingLevelCode = 'JUNIOR' | 'MIDDLE' | 'SENIOR'

export function trainingLevelCode(
  experience: string | null,
): TrainingLevelCode {
  if (experience === 'От 3 до 6 лет') return 'MIDDLE'
  if (experience === 'Более 6 лет') return 'SENIOR'
  return 'JUNIOR'
}

/** Тон для подсветки вероятности оффера. В палитре нет красного, поэтому
 *  «низкая» — нейтральный тон, «средняя» — акцент, «высокая» — pine. */
export type OfferTone = 'low' | 'mid' | 'high'

export const OFFER_TONE: Record<OfferProbability, OfferTone> = {
  Низкая: 'low',
  Средняя: 'mid',
  Высокая: 'high',
}
