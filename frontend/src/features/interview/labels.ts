import type { VacancyStatus } from '@/features/vacancy/api'
import type { InterviewSession, OfferProbability, SessionStatus } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'В процессе',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}

export const VACANCY_STATUS_LABELS: Record<VacancyStatus, string> = {
  ACTIVE: 'активна',
  ARCHIVED: 'в архиве',
  NOT_FOUND: 'удалена',
}

/** Число прохождений: «1 раз», «3 раза», «5 раз». */
export function timesWord(n: number): string {
  const mod100 = n % 100
  if (mod100 >= 11 && mod100 <= 14) return 'раз'
  const mod10 = n % 10
  return mod10 >= 2 && mod10 <= 4 ? 'раза' : 'раз'
}

/** Заголовок интервью — название вакансии. */
export function sessionHeadline(session: {
  vacancyName: InterviewSession['vacancyName']
}): string {
  return session.vacancyName
}

/** Имена, которыми AI-интервьюер представляется кандидату: обычные русские имена
 *  без отчеств — собеседование тренировочное, но разговор должен читаться живым. */
const INTERVIEWER_NAMES = [
  'Анна',
  'Виктор',
  'Дарья',
  'Егор',
  'Ирина',
  'Кирилл',
  'Марина',
  'Никита',
  'Ольга',
  'Павел',
  'Светлана',
  'Тимур',
] as const

/** Имя интервьюера для сессии. Выбор детерминирован по её id, а не случаен на
 *  каждый рендер: иначе имя менялось бы при перезагрузке страницы и при возврате
 *  к незаконченному интервью. Хранить его на бэке ради этого не нужно. */
export function interviewerName(sessionId: string): string {
  let hash = 0
  for (let i = 0; i < sessionId.length; i++) {
    hash = (hash * 31 + sessionId.charCodeAt(i)) | 0
  }
  return INTERVIEWER_NAMES[Math.abs(hash) % INTERVIEWER_NAMES.length]
}

/** Подпись под заголовком — работодатель. */
export function sessionSubtitle(session: {
  employer: InterviewSession['employer']
}): string {
  return session.employer || 'Работодатель не указан'
}

/** Код уровня тренировки по требуемому опыту вакансии: у тренажёра три уровня
 *  сложности, «Нет опыта» и «От 1 года до 3 лет» ведут в лёгкий, нераспознанное — тоже. */
export type TrainingLevelCode = 'EASY' | 'MEDIUM' | 'HARD'

export function trainingLevelCode(
  experience: string | null,
): TrainingLevelCode {
  if (experience === 'От 3 до 6 лет') return 'MEDIUM'
  if (experience === 'Более 6 лет') return 'HARD'
  return 'EASY'
}

/** Тон для подсветки вероятности оффера. В палитре нет красного, поэтому
 *  «низкая» — нейтральный тон, «средняя» — акцент, «высокая» — pine. */
export type OfferTone = 'low' | 'mid' | 'high'

export const OFFER_TONE: Record<OfferProbability, OfferTone> = {
  Низкая: 'low',
  Средняя: 'mid',
  Высокая: 'high',
}
