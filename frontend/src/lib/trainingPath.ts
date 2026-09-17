import type { Training } from '@/content/types'

export function trainingPath({ skill, level, profession }: Training): string {
  const params = new URLSearchParams()
  if (profession) params.set('profession', profession)
  params.set('skill', skill)
  params.set('level', level)
  return `/app/training/new?${params.toString()}`
}
