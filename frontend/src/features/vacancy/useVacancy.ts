import { useQuery } from '@tanstack/react-query'
import { vacancyApi } from './api'

/** Ссылка на конкретную вакансию hh.ru — гейт для запроса предпросмотра. */
export const HH_VACANCY_URL = /^https?:\/\/(www\.)?hh\.ru\/vacancy\/\d+/

export function isHhVacancyUrl(url: string): boolean {
  return HH_VACANCY_URL.test(url.trim())
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
