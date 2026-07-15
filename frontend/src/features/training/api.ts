import { apiFetch } from '@/lib/api'

export type Profession = string
export type Level = string

export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'

export interface TrainingOptions {
  professions: Profession[]
  levels: Level[]
  questionCap: number
  minAnswersToFinish: number
}

export interface CreateTrainingRequest {
  profession: Profession
  level: Level
}

export interface TrainingSession {
  id: string
  profession: Profession
  level: Level
  status: SessionStatus
  answeredCount: number
  created: string
  completedAt: string | null
}

export interface TrainingQuestion {
  questionId: string
  orderIndex: number
  questionText: string
  followUp: boolean
  answerText: string | null
  score: number | null
  feedback: string | null
}

export interface TrainingReport {
  reportId: string
  sessionId: string
  profession: Profession
  level: Level
  avgScore: number | null
  overallFeedback: string
  generatedAt: string
  questions: TrainingQuestion[]
}

/** Обёртка Spring PagedModel: нужен только content, метаданные страницы игнорируем. */
interface Page<T> {
  content: T[]
}

export interface SubmitAnswerVars {
  sessionId: string
  questionId: string
  answerText: string
}

const BASE = '/interview/training'

export const trainingApi = {
  options: () => apiFetch<TrainingOptions>(`${BASE}/options`),

  createSession: (data: CreateTrainingRequest) =>
    apiFetch<TrainingSession>(`${BASE}/sessions`, {
      method: 'POST',
      body: data,
    }),

  listSessions: () =>
    apiFetch<Page<TrainingSession>>(`${BASE}/sessions`).then((p) => p.content),

  getSession: (sessionId: string) =>
    apiFetch<TrainingSession>(`${BASE}/sessions/${sessionId}`),

  nextQuestion: (sessionId: string) =>
    apiFetch<TrainingQuestion>(`${BASE}/sessions/${sessionId}/questions/next`, {
      method: 'POST',
    }),

  submitAnswer: ({ sessionId, questionId, answerText }: SubmitAnswerVars) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}/questions/${questionId}`, {
      method: 'POST',
      body: { answerText },
    }),

  finishSession: (sessionId: string) =>
    apiFetch<TrainingReport>(`${BASE}/sessions/${sessionId}/finish`, {
      method: 'POST',
    }),

  getReport: (sessionId: string) =>
    apiFetch<TrainingReport>(`${BASE}/sessions/${sessionId}/report`),

  deleteSession: (sessionId: string) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}`, { method: 'DELETE' }),
}
