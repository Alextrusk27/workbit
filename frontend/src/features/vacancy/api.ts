import { apiFetch } from '@/lib/api'

export interface VacancyPreview {
  name: string
  employer: string
  salary: string | null
  experience: string | null
  url: string
}

export const vacancyApi = {
  preview: (url: string) =>
    apiFetch<VacancyPreview>('/vacancies/preview', { query: { url } }),
}
