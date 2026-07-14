import type { TrainingQuestion } from '@/features/training/api'

export interface ReportCase {
  main: TrainingQuestion
  followUps: TrainingQuestion[]
}

export function groupReportCases(questions: TrainingQuestion[]): ReportCase[] {
  const cases: ReportCase[] = []
  for (const q of questions) {
    if (!q.followUp || cases.length === 0) {
      cases.push({ main: q, followUps: [] })
    } else {
      cases[cases.length - 1].followUps.push(q)
    }
  }
  return cases
}
