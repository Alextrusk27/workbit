import { describe, expect, it } from 'vitest'
import { FILE_TOO_LARGE_MESSAGE, UNSUPPORTED_FORMAT_MESSAGE } from './errors'
import { MAX_RESUMES, RESUME_ACCEPT, resumeFileError } from './files'

const FIVE_MB = 5 * 1024 * 1024

function file(name: string) {
  return new File(['x'], name)
}

function sizedFile(name: string, size: number) {
  return new File([new Uint8Array(size)], name)
}

describe('RESUME_ACCEPT', () => {
  it('перечисляет PDF, DOCX и TXT', () => {
    expect(RESUME_ACCEPT).toBe('.pdf,.docx,.txt')
  })
})

describe('MAX_RESUMES', () => {
  it('разрешает три резюме', () => {
    expect(MAX_RESUMES).toBe(3)
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

  it('пропускает файл ровно 5 МБ', () => {
    expect(resumeFileError(sizedFile('cv.pdf', FIVE_MB))).toBeNull()
  })

  it('отклоняет файл на байт больше 5 МБ', () => {
    expect(resumeFileError(sizedFile('cv.pdf', FIVE_MB + 1))).toBe(
      FILE_TOO_LARGE_MESSAGE,
    )
  })

  it('у большого файла с неверным расширением сообщает про формат', () => {
    expect(resumeFileError(sizedFile('cv.doc', FIVE_MB + 1))).toBe(
      UNSUPPORTED_FORMAT_MESSAGE,
    )
  })
})
