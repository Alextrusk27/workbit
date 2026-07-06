import { apiFetch } from '@/lib/api'

export type Profession = string
export type Level = string
export type CompanyType = string

export type SessionStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED'

export interface InterviewOptions {
  professions: Profession[]
  levels: Level[]
  companyTypes: CompanyType[]
  minQuestions: number
  maxQuestions: number
}

export interface CreateSessionRequest {
  profession: Profession
  level: Level
  companyType: CompanyType
  totalQuestions: number
}

export interface SessionResponse {
  id: string
  profession: Profession
  companyType: CompanyType
  level: Level
  status: SessionStatus
  totalQuestions: number
  answeredCount: number
  created: string
  completedAt: string | null
}

export interface QuestionResponse {
  questionId: string
  orderIndex: number
  questionText: string
  answerText: string | null
  score: number | null
  feedback: string | null
}

export interface SessionReport {
  reportId: string
  sessionId: string
  profession: Profession
  companyType: CompanyType
  level: Level
  totalQuestions: number
  avgScore: number
  overallFeedback: string
  offerProbability: string
  generatedAt: string
}

export interface SubmitAnswerVars {
  sessionId: string
  questionId: string
  answerText: string
  evaluate: boolean
}

export const interviewApi = {
  options: () => apiFetch<InterviewOptions>('/interview/options'),

  createSession: (data: CreateSessionRequest) =>
    apiFetch<SessionResponse>('/interview/sessions', {
      method: 'POST',
      body: data,
    }),

  listSessions: () => apiFetch<SessionResponse[]>('/interview/sessions'),

  getSession: (sessionId: string) =>
    apiFetch<SessionResponse>(`/interview/sessions/${sessionId}`),

  getQuestion: (sessionId: string, index: number) =>
    apiFetch<QuestionResponse>(
      `/interview/sessions/${sessionId}/questions/${index}`,
    ),

  submitAnswer: ({
    sessionId,
    questionId,
    answerText,
    evaluate,
  }: SubmitAnswerVars) =>
    apiFetch<QuestionResponse>(
      `/interview/sessions/${sessionId}/questions/${questionId}`,
      { method: 'POST', body: { answerText }, query: { evaluate } },
    ),

  finishSession: (sessionId: string) =>
    apiFetch<SessionReport>(`/interview/sessions/${sessionId}/finish`, {
      method: 'POST',
    }),

  getReport: (sessionId: string) =>
    apiFetch<SessionReport>(`/interview/sessions/${sessionId}/report`),

  deleteSession: (sessionId: string) =>
    apiFetch<void>(`/interview/sessions/${sessionId}`, { method: 'DELETE' }),
}
