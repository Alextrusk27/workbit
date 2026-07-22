import { apiFetch } from '@/lib/api'

export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'

/** Минимум отвеченных вопросов для завершения интервью. Зеркалит
 *  InterviewService.MIN_ANSWERED_TO_FINISH на бэке — у интервью нет /options-ручки,
 *  как у тренажёра. Финальный гейт всё равно за бэком (409 при нехватке). */
export const MIN_ANSWERS_TO_FINISH = 3

export interface CreateInterviewRequest {
  vacancyUrl: string
}

export interface InterviewSession {
  id: string
  vacancyName: string
  employer: string
  status: SessionStatus
  answeredCount: number
  created: string
  completedAt: string | null
}

export interface InterviewQuestion {
  questionId: string
  orderIndex: number
  questionText: string
  answerText: string | null
  score: number | null
  feedback: string | null
}

/** Вероятность оффера приходит уже русским лейблом — на бэке enum
 *  `InterviewReport.OfferProbability` сериализуется через `@JsonValue`. */
export type OfferProbability = 'Низкая' | 'Средняя' | 'Высокая'

export interface InterviewReport {
  reportId: string
  sessionId: string
  avgScore: number | null
  offerProbability: OfferProbability
  overallFeedback: string
  generatedAt: string
  questions: InterviewQuestion[]
}

export interface SubmitAnswerVars {
  sessionId: string
  questionId: string
  answerText: string
}

const BASE = '/interview'

export const interviewApi = {
  createSession: (data: CreateInterviewRequest) =>
    apiFetch<InterviewSession>(`${BASE}/sessions`, {
      method: 'POST',
      body: data,
    }),

  listSessions: () => apiFetch<InterviewSession[]>(`${BASE}/sessions`),

  getSession: (sessionId: string) =>
    apiFetch<InterviewSession>(`${BASE}/sessions/${sessionId}`),

  nextQuestion: (sessionId: string) =>
    apiFetch<InterviewQuestion>(
      `${BASE}/sessions/${sessionId}/questions/next`,
      { method: 'POST' },
    ),

  submitAnswer: ({ sessionId, questionId, answerText }: SubmitAnswerVars) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}/questions/${questionId}`, {
      method: 'POST',
      body: { answerText },
    }),

  finishSession: (sessionId: string) =>
    apiFetch<InterviewReport>(`${BASE}/sessions/${sessionId}/finish`, {
      method: 'POST',
    }),

  getReport: (sessionId: string) =>
    apiFetch<InterviewReport>(`${BASE}/sessions/${sessionId}/report`),

  deleteSession: (sessionId: string) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}`, { method: 'DELETE' }),
}
