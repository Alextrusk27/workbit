import { UNSUPPORTED_FORMAT_MESSAGE } from './errors'

const RESUME_EXTENSIONS = ['.pdf', '.docx', '.txt']

export const RESUME_ACCEPT = RESUME_EXTENSIONS.join(',')

/** Быстрая проверка до отправки. Окончательно формат проверяет бэк по содержимому. */
export function resumeFileError(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!RESUME_EXTENSIONS.some((ext) => name.endsWith(ext))) {
    return UNSUPPORTED_FORMAT_MESSAGE
  }
  return null
}
