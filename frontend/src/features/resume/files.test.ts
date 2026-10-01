import { describe, expect, it } from 'vitest'
import { UNSUPPORTED_FORMAT_MESSAGE } from './errors'
import { RESUME_ACCEPT, resumeFileError } from './files'

function file(name: string) {
  return new File(['x'], name)
}

describe('RESUME_ACCEPT', () => {
  it('перечисляет PDF, DOCX и TXT', () => {
    expect(RESUME_ACCEPT).toBe('.pdf,.docx,.txt')
  })
})

describe('resumeFileError', () => {
  it.each(['cv.pdf', 'cv.docx', 'cv.txt', 'CV.PDF', 'Resume.Docx', 'a.TxT'])(
    'пропускает %s',
    (name) => {
      expect(resumeFileError(file(name))).toBeNull()
    },
  )

  it.each(['cv.doc', 'cv.xlsx', 'photo.png', 'resume', 'cv.pdf.exe'])(
    'отклоняет %s',
    (name) => {
      expect(resumeFileError(file(name))).toBe(UNSUPPORTED_FORMAT_MESSAGE)
    },
  )
})
