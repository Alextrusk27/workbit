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
  topic: string | null
  level: Level
}

export interface NormalizeInputRequest {
  profession: string
  topic: string | null
}

export interface NormalizeInputResponse {
  professionRecognized: boolean
  professionSuggestions: string[]
  topicRecognized: boolean | null
  topicSuggestions: string[] | null
  topicFitsProfession: boolean | null
}

export interface TrainingSession {
  id: string
  profession: Profession
  topic: string | null
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
  topic: string | null
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

const BASE = '/training'

export const trainingApi = {
  options: () => apiFetch<TrainingOptions>(`${BASE}/options`),

  suggestProfessions: (query: string) =>
    apiFetch<Profession[]>(
      `${BASE}/suggest/professions?query=${encodeURIComponent(query)}`,
    ),

  suggestTopics: (profession: string, query: string) =>
    apiFetch<string[]>(
      `${BASE}/suggest/topics?profession=${encodeURIComponent(profession)}&query=${encodeURIComponent(query)}`,
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
