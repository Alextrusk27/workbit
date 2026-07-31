import { apiFetch } from '@/lib/api'

export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'

export interface CreateInterviewRequest {
  vacancyUrl: string
}

export interface InterviewSession {
  id: string
  vacancyName: string
  employer: string
  vacancyUrl: string | null
  experience: string | null
  status: SessionStatus
  answeredCount: number
  totalQuestions: number
  created: string
  completedAt: string | null
}

export interface InterviewQuestion {
  questionId: string
  orderIndex: number
  questionText: string
  followUp: boolean
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
  recommendations: string | null
  weakestSkill: string | null
  generatedAt: string
  questions: InterviewQuestion[]
}

export interface SubmitAnswerVars {
  sessionId: string
  questionId: string
  answerText: string
}

export interface InterviewVacancy {
  vacancyId: string
  vacancyName: string
  employer: string
  vacancyUrl: string | null
  experience: string | null
  status: SessionStatus
  completedCount: number
  bestScore: number | null
  bestOffer: OfferProbability | null
  lastActivity: string
}

export interface InterviewAttempt {
  sessionId: string
  status: SessionStatus
  created: string
  completedAt: string | null
  avgScore: number | null
  offerProbability: OfferProbability | null
}

export interface RecommendedTraining {
  skill: string
  interviewScore: number | null
  trainingSessionId: string | null
  trainingStatus: SessionStatus | null
  trainingScore: number | null
  answeredCount: number | null
  totalQuestions: number | null
}

export interface InterviewVacancyDetail {
  vacancyId: string
  vacancyName: string
  employer: string
  vacancyUrl: string | null
  experience: string | null
  interviews: InterviewAttempt[]
  recommendedTrainings: RecommendedTraining[]
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

  listVacancies: () => apiFetch<InterviewVacancy[]>(`${BASE}/vacancies`),

  getVacancy: (vacancyId: string) =>
    apiFetch<InterviewVacancyDetail>(`${BASE}/vacancies/${vacancyId}`),

  deleteVacancy: (vacancyId: string) =>
    apiFetch<void>(`${BASE}/vacancies/${vacancyId}`, { method: 'DELETE' }),
}
