import { apiFetch } from '@/lib/api'

export interface VacancyPreview {
  name: string
  employer: string
  salary: string | null
  experience: string | null
  url: string
}

export type VacancyStatus = 'ACTIVE' | 'ARCHIVED' | 'NOT_FOUND'

export interface VacancyStatusResponse {
  status: VacancyStatus
}

export const vacancyApi = {
  preview: (url: string) =>
    apiFetch<VacancyPreview>('/vacancies/preview', { query: { url } }),

  status: (url: string) =>
    apiFetch<VacancyStatusResponse>('/vacancies/status', { query: { url } }),
}
