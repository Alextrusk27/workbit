import { interviewQuestions } from '@/content/articles/interview-questions'
import type { ArticleEntry } from '@/content/articles/types'

export const articles: Record<string, ArticleEntry[]> = {
  'interview-questions': interviewQuestions,
}

export function findArticle(rubric = '', slug = ''): ArticleEntry | undefined {
  return articles[rubric]?.find((a) => a.slug === slug)
}
