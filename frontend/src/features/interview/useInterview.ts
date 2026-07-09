import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  interviewApi,
  type CreateSessionByVacancyRequest,
  type CreateSessionRequest,
  type QuestionResponse,
  type SubmitAnswerVars,
} from './api'

const keys = {
  options: ['interview', 'options'] as const,
  sessions: ['interview', 'sessions'] as const,
  session: (id: string) => ['interview', 'session', id] as const,
  question: (sessionId: string, index: number) =>
    ['interview', 'question', sessionId, index] as const,
  report: (id: string) => ['interview', 'report', id] as const,
}

export function useInterviewOptions() {
  return useQuery({
    queryKey: keys.options,
    queryFn: interviewApi.options,
    staleTime: Infinity,
  })
}

export function useSessions() {
  return useQuery({
    queryKey: keys.sessions,
    queryFn: interviewApi.listSessions,
  })
}

export function useSession(sessionId: string) {
  return useQuery({
    queryKey: keys.session(sessionId),
    queryFn: () => interviewApi.getSession(sessionId),
  })
}

export function useQuestion(sessionId: string, index: number, enabled = true) {
  return useQuery({
    queryKey: keys.question(sessionId, index),
    queryFn: () => interviewApi.getQuestion(sessionId, index),
    enabled: enabled && index >= 1,
  })
}

export function useReport(sessionId: string) {
  return useQuery({
    queryKey: keys.report(sessionId),
    queryFn: () => interviewApi.getReport(sessionId),
  })
}

export function useTranscript(sessionId: string, total: number) {
  return useQueries({
    queries: Array.from({ length: total }, (_, i) => ({
      queryKey: keys.question(sessionId, i + 1),
      queryFn: () => interviewApi.getQuestion(sessionId, i + 1),
    })),
    combine: (results) => ({
      questions: results
        .map((r) => r.data)
        .filter((q): q is QuestionResponse => q != null),
      isLoading: results.some((r) => r.isLoading),
      isError: results.some((r) => r.isError),
    }),
  })
}

export function useCreateSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateSessionRequest) =>
      interviewApi.createSession(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}

export function useCreateSessionByVacancy() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateSessionByVacancyRequest) =>
      interviewApi.createSessionByVacancy(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}

export function useSubmitAnswer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vars: SubmitAnswerVars) => interviewApi.submitAnswer(vars),
    onSuccess: (data, vars) => {
      qc.setQueryData(keys.question(vars.sessionId, data.orderIndex), data)
      qc.invalidateQueries({ queryKey: keys.session(vars.sessionId) })
    },
  })
}

export function useFinishSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => interviewApi.finishSession(sessionId),
    onSuccess: (report, sessionId) => {
      qc.setQueryData(keys.report(sessionId), report)
      qc.invalidateQueries({ queryKey: keys.sessions })
      qc.invalidateQueries({ queryKey: keys.session(sessionId) })
    },
  })
}

export function useDeleteSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => interviewApi.deleteSession(sessionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}
