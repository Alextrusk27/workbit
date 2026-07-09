import type { SessionResponse, SessionSource, SessionStatus } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Не начато',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}

export const SOURCE_LABELS: Record<SessionSource, string> = {
  CATALOG: 'Тренировки',
  VACANCY: 'По вакансии',
}

/** Заголовок сессии: профессия для каталога, название вакансии — для вакансии. */
export function sessionHeadline(session: SessionResponse): string {
  if (session.source === 'VACANCY') {
    return session.vacancy?.name ?? 'Интервью по вакансии'
  }
  return session.profession ?? 'Интервью'
}

/** Подпись под заголовком: уровень·компания для каталога, работодатель·опыт — для вакансии. */
export function sessionSubtitle(session: SessionResponse): string {
  if (session.source === 'VACANCY') {
    return [
      session.vacancy?.employer ?? 'Текст вакансии',
      session.vacancy?.experience,
    ]
      .filter(Boolean)
      .join(' · ')
  }
  return [session.level, session.companyType].filter(Boolean).join(' · ')
}
