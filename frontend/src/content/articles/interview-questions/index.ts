import { readingMinutes } from '@/content/articles/readingMinutes'
import type { ArticleEntry } from '@/content/articles/types'
import * as salesManager from './sales-manager'

export const interviewQuestions: ArticleEntry[] = [
  {
    slug: 'sales-manager',
    name: 'Менеджер по продажам',
    published: '2026-09-15',
    readingMinutes: readingMinutes(salesManager.article),
    teaser:
      'Узнай, что на самом деле проверяют на собеседовании в продажах, и подготовь ответы про возражения и холодные звонки',
    title:
      'Вопросы на собеседовании менеджера по продажам: что спрашивают и как отвечать | Workbit',
    description:
      'Какие вопросы задают менеджеру по продажам на собеседовании и что хотят услышать: этапы сделки, возражения, холодные звонки, ролевая игра. Разбор с нуля и тренировка с ИИ.',
    load: async () => salesManager,
  },
]
