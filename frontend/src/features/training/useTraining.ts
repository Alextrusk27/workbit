import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  trainingApi,
  type CreateTrainingRequest,
  type NormalizeInputRequest,
  type SubmitAnswerVars,
} from './api'

const MIN_SUGGEST_QUERY = 2

const keys = {
  options: ['training', 'options'] as const,
  sessions: ['training', 'sessions'] as const,
  session: (id: string) => ['training', 'session', id] as const,
  report: (id: string) => ['training', 'report', id] as const,
  professionSuggest: (query: string) =>
    ['training', 'suggest', 'professions', query] as const,
  skillSuggest: (profession: string, query: string) =>
    ['training', 'suggest', 'skills', profession, query] as const,
  referenceAnswer: (sessionId: string, questionId: string) =>
    ['training', 'reference-answer', sessionId, questionId] as const,
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

export function useProfessionSuggest(query: string) {
  return useQuery({
    queryKey: keys.professionSuggest(query),
    queryFn: () => trainingApi.suggestProfessions(query),
    enabled: query.trim().length >= MIN_SUGGEST_QUERY,
    staleTime: 5 * 60 * 1000,
  })
}

export function useSkillSuggest(profession: string, query: string) {
  return useQuery({
    queryKey: keys.skillSuggest(profession, query),
    queryFn: () => trainingApi.suggestSkills(profession, query),
    enabled: query.trim().length >= MIN_SUGGEST_QUERY,
    staleTime: 5 * 60 * 1000,
  })
}

/** Эталонный ответ грузится только по кнопке: enabled поднимает вызывающий. */
export function useReferenceAnswer(
  sessionId: string,
  questionId: string,
  enabled: boolean,
) {
  return useQuery({
    queryKey: keys.referenceAnswer(sessionId, questionId),
    queryFn: () => trainingApi.referenceAnswer(sessionId, questionId),
    enabled,
    staleTime: Infinity,
  })
}

export function useNormalizeInput() {
  return useMutation({
    mutationFn: (data: NormalizeInputRequest) =>
      trainingApi.normalizeInput(data),
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

export function useAddQuestions() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => trainingApi.addQuestions(sessionId),
    onSuccess: (session) => {
      qc.setQueryData(keys.session(session.id), session)
      qc.invalidateQueries({ queryKey: keys.sessions })
    },
  })
}

/** Перезапуск стирает разбор — отчёт выкидываем из кэша, иначе страница покажет старый. */
export function useRestartSession() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (sessionId: string) => trainingApi.restartSession(sessionId),
    onSuccess: (session) => {
      qc.setQueryData(keys.session(session.id), session)
      qc.removeQueries({ queryKey: keys.report(session.id) })
      qc.invalidateQueries({ queryKey: keys.sessions })
    },
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
