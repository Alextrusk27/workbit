import { apiFetch, apiFetchBlob } from '@/lib/api'

export type ResumeFormat = 'PDF' | 'DOCX' | 'TXT'

export interface Resume {
  id: string
  name: string
  format: ResumeFormat
  originalFilename: string
  sizeBytes: number
  uploadedAt: string
}

const BASE = '/resumes'

export const resumeApi = {
  list: () => apiFetch<Resume[]>(BASE),

  upload: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return apiFetch<Resume>(BASE, { method: 'POST', body: formData })
  },

  remove: (id: string) => apiFetch<void>(`${BASE}/${id}`, { method: 'DELETE' }),

  file: (id: string) => apiFetchBlob(`${BASE}/${id}/file`),
}
