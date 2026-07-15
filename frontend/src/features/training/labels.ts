import type { SessionStatus, TrainingSession } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Не начато',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}

/** Заголовок тренировки — профессия. */
export function sessionHeadline(session: {
  profession: TrainingSession['profession']
}): string {
  return session.profession
}

/** Подпись под заголовком — уровень. */
export function sessionSubtitle(session: {
  level: TrainingSession['level']
}): string {
  return session.level
}
