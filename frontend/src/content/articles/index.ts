import { interviewQuestions } from '@/content/articles/interview-questions'
import type { ArticleEntry, Rubric } from '@/content/articles/types'

export const rubrics: Record<string, Rubric> = {
  'interview-questions': {
    label: 'Вопросы на собеседовании',
    entries: interviewQuestions,
  },
}

export function findRubric(rubric = ''): Rubric | undefined {
  return Object.hasOwn(rubrics, rubric) ? rubrics[rubric] : undefined
}

export function findArticle(rubric = '', slug = ''): ArticleEntry | undefined {
  return findRubric(rubric)?.entries.find((a) => a.slug === slug)
}
