import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reachGoal } from '@/lib/metrika'
import { resumeApi, type Resume } from './api'
import { saveBlob } from './preview'

export const resumeKeys = {
  list: ['resumes'] as const,
  file: (id: string) => ['resume-file', id] as const,
}

export function useResumes() {
  return useQuery({
    queryKey: resumeKeys.list,
    queryFn: resumeApi.list,
  })
}

export function useUploadResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: resumeApi.upload,
    onSuccess: () => {
      reachGoal('resume_upload')
      qc.invalidateQueries({ queryKey: resumeKeys.list })
    },
  })
}

export function useRenameResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, name }: { id: string; name: string }) =>
      resumeApi.rename(id, name),
    onSettled: () => qc.invalidateQueries({ queryKey: resumeKeys.list }),
  })
}

export function useDeleteResume() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: resumeApi.remove,
    onSettled: () => qc.invalidateQueries({ queryKey: resumeKeys.list }),
  })
}

export function useResumeFile(id: string) {
  return useQuery({
    queryKey: resumeKeys.file(id),
    queryFn: () => resumeApi.file(id),
    staleTime: Infinity,
    gcTime: 0,
  })
}

export function useDownloadResume() {
  return useMutation({
    mutationFn: (resume: Resume) => resumeApi.file(resume.id),
    onSuccess: (blob, resume) => saveBlob(blob, resume.originalFilename),
  })
}
