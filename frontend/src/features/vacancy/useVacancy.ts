import { useQuery } from '@tanstack/react-query'
import { vacancyApi } from './api'

/** Ссылка на конкретную вакансию hh.ru — гейт для запроса предпросмотра. */
export const HH_VACANCY_URL = /^https?:\/\/(www\.)?hh\.ru\/vacancy\/(?<id>\d+)/

export function isHhVacancyUrl(url: string): boolean {
  return HH_VACANCY_URL.test(url.trim())
}

/** Id вакансии из ссылки: он же `vacancyId` в ручках интервью (sourceId снапшота). */
export function hhVacancyId(url: string): string {
  return HH_VACANCY_URL.exec(url.trim())?.groups?.id ?? ''
}

export function useVacancyPreview(url: string) {
  const trimmed = url.trim()
  return useQuery({
    queryKey: ['vacancy', 'preview', trimmed],
    queryFn: () => vacancyApi.preview(trimmed),
    enabled: isHhVacancyUrl(trimmed),
    retry: false,
    staleTime: 5 * 60_000,
  })
}

export function useVacancyStatus(url: string | null) {
  const trimmed = (url ?? '').trim()
  return useQuery({
    queryKey: ['vacancy', 'status', trimmed],
    queryFn: () => vacancyApi.status(trimmed),
    enabled: isHhVacancyUrl(trimmed),
    retry: false,
    staleTime: 30 * 60_000,
  })
}
