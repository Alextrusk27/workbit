import { FILE_TOO_LARGE_MESSAGE, UNSUPPORTED_FORMAT_MESSAGE } from './errors'

const RESUME_EXTENSIONS = ['.pdf', '.docx', '.txt']
const MAX_RESUME_BYTES = 5 * 1024 * 1024

export const MAX_RESUMES = 3

export const RESUME_ACCEPT = RESUME_EXTENSIONS.join(',')

/** Быстрая проверка до отправки. Окончательно файл проверяет бэк. */
export function resumeFileError(file: File): string | null {
  const name = file.name.toLowerCase()
  if (!RESUME_EXTENSIONS.some((ext) => name.endsWith(ext))) {
    return UNSUPPORTED_FORMAT_MESSAGE
  }
  if (file.size > MAX_RESUME_BYTES) {
    return FILE_TOO_LARGE_MESSAGE
  }
  return null
}
