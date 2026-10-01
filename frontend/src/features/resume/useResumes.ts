import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { reachGoal } from '@/lib/metrika'
import { resumeApi } from './api'

export const resumeKeys = {
  list: ['resumes'] as const,
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
