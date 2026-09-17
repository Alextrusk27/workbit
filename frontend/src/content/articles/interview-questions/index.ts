import type { ArticleEntry } from '@/content/articles/types'

export const interviewQuestions: ArticleEntry[] = [
  {
    slug: 'sales-manager',
    title:
      'Вопросы на собеседовании менеджера по продажам: что спрашивают и как отвечать | Workbit',
    description:
      'Какие вопросы задают менеджеру по продажам на собеседовании и что хотят услышать: этапы сделки, возражения, холодные звонки, ролевая игра. Разбор с нуля и тренировка с ИИ.',
    load: () => import('./sales-manager'),
  },
]
