import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { billingKeys } from '@/features/billing/useBilling'
import { finishWithReportFallback } from '@/lib/api'
import {
  interviewApi,
  type CreateInterviewRequest,
  type SubmitAnswerVars,
} from './api'

const keys = {
  session: (id: string) => ['interview', 'session', id] as const,
  report: (id: string) => ['interview', 'report', id] as const,
  vacancies: ['interview', 'vacancies'] as const,
  vacancy: (id: string) => ['interview', 'vacancy', id] as const,
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

export function useInterviewVacancies() {
  return useQuery({
    queryKey: keys.vacancies,
    queryFn: interviewApi.listVacancies,
  })
}

export function useInterviewVacancy(vacancyId: string, enabled = true) {
  return useQuery({
    queryKey: keys.vacancy(vacancyId),
    queryFn: () => interviewApi.getVacancy(vacancyId),
    enabled,
  })
}

export function useCreateInterview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateInterviewRequest) =>
      interviewApi.createSession(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.vacancies })
      qc.invalidateQueries({ queryKey: ['interview', 'vacancy'] })
      qc.invalidateQueries({ queryKey: billingKeys.quota })
    },
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
    mutationFn: (sessionId: string) =>
      finishWithReportFallback(
        () => interviewApi.finishSession(sessionId),
        () => interviewApi.getReport(sessionId),
      ),
    onSuccess: (report, sessionId) => {
      qc.setQueryData(keys.report(sessionId), report)
      qc.invalidateQueries({ queryKey: keys.session(sessionId) })
      qc.invalidateQueries({ queryKey: keys.vacancies })
      qc.invalidateQueries({ queryKey: ['interview', 'vacancy'] })
    },
  })
}

export function useDeleteInterviewVacancy() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (vacancyId: string) => interviewApi.deleteVacancy(vacancyId),
    onSuccess: (_, vacancyId) => {
      qc.removeQueries({ queryKey: keys.vacancy(vacancyId) })
      qc.invalidateQueries({ queryKey: keys.vacancies })
    },
  })
}
