import type { SessionStatus } from './api'

export const STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: 'Не начато',
  IN_PROGRESS: 'В процессе',
  COMPLETED: 'Завершено',
}
