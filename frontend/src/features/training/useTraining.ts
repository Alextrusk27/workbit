import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  trainingApi,
  type CreateTrainingRequest,
  type SubmitAnswerVars,
} from './api'

const keys = {
  options: ['training', 'options'] as const,
  sessions: ['training', 'sessions'] as const,
  session: (id: string) => ['training', 'session', id] as const,
  report: (id: string) => ['training', 'report', id] as const,
}

export function useTrainingOptions() {
  return useQuery({
    queryKey: keys.options,
    queryFn: trainingApi.options,
    staleTime: Infinity,
  })
}

export function useSessions() {
  return useQuery({
    queryKey: keys.sessions,
    queryFn: trainingApi.listSessions,
  })
}

export function useSession(sessionId: string) {
  return useQuery({
    queryKey: keys.session(sessionId),
    queryFn: () => trainingApi.getSession(sessionId),
  })
}

export function useReport(sessionId: string, enabled = true) {
  return useQuery({
    queryKey: keys.report(sessionId),
    queryFn: () => trainingApi.getReport(sessionId),
    enabled,
  })
}

export function useCreateSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateTrainingRequest) =>
      trainingApi.createSession(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}

export function useSubmitAnswer() {
  return useMutation({
    mutationFn: (vars: SubmitAnswerVars) => trainingApi.submitAnswer(vars),
  })
}

export function useFinishSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => trainingApi.finishSession(sessionId),
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
    mutationFn: (sessionId: string) => trainingApi.deleteSession(sessionId),
    onSuccess: () => qc.invalidateQueries({ queryKey: keys.sessions }),
  })
}
