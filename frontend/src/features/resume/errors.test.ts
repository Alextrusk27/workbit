import { describe, expect, it } from 'vitest'
import { ApiRequestError, getErrorMessage } from '../../lib/api'
import { UNSUPPORTED_FORMAT_MESSAGE, resumeErrorMessage } from './errors'

function apiError(detail: string, message = 'Bad request') {
  return new ApiRequestError(400, {
    timestamp: '2026-10-01T00:00:00Z',
    status: 'BAD_REQUEST',
    message,
    errors: [detail],
  })
}

describe('resumeErrorMessage', () => {
  it('неподдерживаемый формат переводит в список допустимых', () => {
    expect(resumeErrorMessage(apiError('Unsupported format'))).toBe(
      'Поддерживаются только PDF, DOCX и TXT.',
    )
    expect(UNSUPPORTED_FORMAT_MESSAGE).toBe(
      'Поддерживаются только PDF, DOCX и TXT.',
    )
  })

  it('старый формат Word объясняет, как пересохранить файл', () => {
    expect(resumeErrorMessage(apiError('Legacy format'))).toBe(
      'Старые файлы Word, RTF и документы Word под паролем не поддерживаются. Сохрани файл как DOCX без пароля или PDF.',
    )
  })

  it('неизвестную деталь отдаёт как getErrorMessage', () => {
    const error = apiError('Something else', 'Файл повреждён')
    expect(resumeErrorMessage(error)).toBe(getErrorMessage(error))
    expect(resumeErrorMessage(error)).toBe('Файл повреждён')
  })

  it('не-API ошибку отдаёт как getErrorMessage', () => {
    const error = new TypeError('Failed to fetch')
    expect(resumeErrorMessage(error)).toBe(getErrorMessage(error))
  })
})
