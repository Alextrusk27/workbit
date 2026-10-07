import { interviewQuestions } from '@/content/articles/interview-questions'
import type { ArticleEntry, Rubric } from '@/content/articles/types'

export const rubrics: Record<string, Rubric> = {
  'interview-questions': {
    label: 'Вопросы на собеседовании',
    title:
      'Вопросы на собеседовании по профессиям: что спрашивают и как отвечать | Workbit',
    description:
      'Разборы вопросов на собеседовании по профессиям: что проверяет работодатель, зачем задают каждый вопрос и что хотят услышать в ответ.',
    hero: {
      lead: 'Вопросы на собеседовании',
      accent: 'по профессиям',
      text: 'Что спрашивают на собеседовании в разных профессиях, зачем это спрашивают и что хотят услышать в ответ.',
    },
    entries: interviewQuestions,
  },
}

export function findRubric(rubric = ''): Rubric | undefined {
  return Object.hasOwn(rubrics, rubric) ? rubrics[rubric] : undefined
}

export function findArticle(rubric = '', slug = ''): ArticleEntry | undefined {
  return findRubric(rubric)?.entries.find((a) => a.slug === slug)
}
