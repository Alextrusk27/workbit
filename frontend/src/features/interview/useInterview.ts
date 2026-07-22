import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  interviewApi,
  type CreateInterviewRequest,
  type SubmitAnswerVars,
} from './api'

const keys = {
  sessions: ['interview', 'sessions'] as const,
  session: (id: string) => ['interview', 'session', id] as const,
  report: (id: string) => ['interview', 'report', id] as const,
}

export function useInterviewSessions() {
  return useQuery({
    queryKey: keys.sessions,
    queryFn: interviewApi.listSessions,
  })
}

export function useInterviewSession(sessionId: string) {
  return useQuery({
    queryKey: keys.session(sessionId),
    queryFn: () => interviewApi.getSession(sessionId),
  })
}

export function useInterviewReport(sessionId: string, enabled = true) {
  return useQuery({
    queryKey: keys.report(sessionId),
    queryFn: () => interviewApi.getReport(sessionId),
    enabled,
  })
}

export function useCreateInterview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateInterviewRequest) =>
      interviewApi.createSession(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}

export function useSubmitInterviewAnswer() {
  return useMutation({
    mutationFn: (vars: SubmitAnswerVars) => interviewApi.submitAnswer(vars),
  })
}

export function useFinishInterview() {
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

export function useDeleteInterview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => interviewApi.deleteSession(sessionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}
