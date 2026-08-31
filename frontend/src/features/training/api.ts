import { apiFetch } from '@/lib/api'

export type Profession = string
export type Level = string

export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'

export interface TrainingOptions {
  skills: string[]
  professions: Profession[]
  levels: Level[]
  questionCap: number
  maxQuestions: number
  minAnswersToFinish: number
}

export interface CreateTrainingRequest {
  skill: string
  profession: Profession
  level: Level
}

export interface NormalizeInputRequest {
  skill: string
  profession: string
}

export interface NormalizeInputResponse {
  skillRecognized: boolean
  skillSuggestions: string[]
  professionRecognized: boolean
  professionSuggestions: string[]
  skillFitsProfession: boolean
}

export interface TrainingSession {
  id: string
  skill: string
  profession: Profession
  level: Level
  status: SessionStatus
  answeredCount: number
  totalQuestions: number
  created: string
  completedAt: string | null
}

export interface TrainingQuestion {
  questionId: string
  orderIndex: number
  questionText: string
  answerText: string | null
  score: number | null
  feedback: string | null
}

export interface TrainingReport {
  reportId: string
  sessionId: string
  skill: string
  profession: Profession
  level: Level
  avgScore: number | null
  overallFeedback: string
  generatedAt: string
  questions: TrainingQuestion[]
}

export interface ReferenceAnswer {
  answer: string
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

export interface FeedbackRequest {
  vote: 'UP' | 'DOWN'
  reasons: string[]
  comment?: string
}

const BASE = '/training'

export const trainingApi = {
  options: () => apiFetch<TrainingOptions>(`${BASE}/options`),

  suggestProfessions: (query: string) =>
    apiFetch<Profession[]>(
      `${BASE}/suggest/professions?query=${encodeURIComponent(query)}`,
    ),

  suggestSkills: (profession: string, query: string) =>
    apiFetch<string[]>(
      `${BASE}/suggest/skills?query=${encodeURIComponent(query)}` +
        (profession ? `&profession=${encodeURIComponent(profession)}` : ''),
    ),

  normalizeInput: (data: NormalizeInputRequest) =>
    apiFetch<NormalizeInputResponse>(`${BASE}/normalize`, {
      method: 'POST',
      body: data,
    }),

  createSession: (data: CreateTrainingRequest) =>
    apiFetch<TrainingSession>(`${BASE}/sessions`, {
      method: 'POST',
      body: data,
    }),

  listSessions: () =>
    apiFetch<Page<TrainingSession>>(`${BASE}/sessions`).then((p) => p.content),

  getSession: (sessionId: string) =>
    apiFetch<TrainingSession>(`${BASE}/sessions/${sessionId}`),

  answeredQuestions: (sessionId: string) =>
    apiFetch<TrainingQuestion[]>(`${BASE}/sessions/${sessionId}/questions`),

  nextQuestion: (sessionId: string) =>
    apiFetch<TrainingQuestion>(`${BASE}/sessions/${sessionId}/questions/next`, {
      method: 'POST',
    }),

  addQuestions: (sessionId: string) =>
    apiFetch<TrainingSession>(`${BASE}/sessions/${sessionId}/questions/more`, {
      method: 'POST',
    }),

  restartSession: (sessionId: string) =>
    apiFetch<TrainingSession>(`${BASE}/sessions/${sessionId}/restart`, {
      method: 'POST',
    }),

  referenceAnswer: (sessionId: string, questionId: string) =>
    apiFetch<ReferenceAnswer>(
      `${BASE}/sessions/${sessionId}/questions/${questionId}/reference-answer`,
    ),

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

  questionFeedback: (
    sessionId: string,
    questionId: string,
    body: FeedbackRequest,
  ) =>
    apiFetch<void>(
      `${BASE}/sessions/${sessionId}/questions/${questionId}/feedback`,
      { method: 'POST', body },
    ),

  reportFeedback: (sessionId: string, body: FeedbackRequest) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}/report/feedback`, {
      method: 'POST',
      body,
    }),

  deleteSession: (sessionId: string) =>
    apiFetch<void>(`${BASE}/sessions/${sessionId}`, { method: 'DELETE' }),
}
