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

/** Тон для подсветки вероятности оффера. В палитре нет красного, поэтому
 *  «низкая» — нейтральный тон, «средняя» — акцент, «высокая» — pine. */
export type OfferTone = 'low' | 'mid' | 'high'

export const OFFER_TONE: Record<OfferProbability, OfferTone> = {
  Низкая: 'low',
  Средняя: 'mid',
  Высокая: 'high',
}
