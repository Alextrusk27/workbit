import type { SessionStatus, TrainingSession } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Не начато',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}

/** Заголовок тренировки — навык: тренируется он, профессия только уточняет. */
export function sessionHeadline(session: {
  skill: TrainingSession['skill']
}): string {
  return session.skill
}

/** Подпись под заголовком — уровень и профессия. */
export function sessionSubtitle(session: {
  level: TrainingSession['level']
  profession: TrainingSession['profession']
}): string {
  return `${session.level} · ${session.profession}`
}
