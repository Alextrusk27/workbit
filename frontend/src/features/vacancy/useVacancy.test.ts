import { describe, expect, it } from 'vitest'
import { isHhVacancyUrl, isPreviewableVacancyUrl } from './useVacancy'

describe('isPreviewableVacancyUrl', () => {
  it('недописанный ID не запрашивает превью, хотя ссылка валидна', () => {
    const url = 'https://hh.ru/vacancy/13801960'
    expect(isHhVacancyUrl(url)).toBe(true)
    expect(isPreviewableVacancyUrl(url)).toBe(false)
  })

  it('ID из 9 цифр запрашивает превью', () => {
    expect(isPreviewableVacancyUrl('https://hh.ru/vacancy/138019607')).toBe(true)
  })

  it('региональная ссылка с параметрами запрашивает превью', () => {
    expect(
      isPreviewableVacancyUrl(
        'https://samara.hh.ru/vacancy/137596468?from=applicant_recommended',
      ),
    ).toBe(true)
  })

  it('не ссылка на вакансию hh.ru превью не запрашивает', () => {
    expect(isPreviewableVacancyUrl('https://example.com/vacancy/138019607')).toBe(
      false,
    )
  })
})
