import { readingMinutes } from '@/content/articles/readingMinutes'
import type { ArticleEntry } from '@/content/articles/types'
import * as accountant from './accountant'
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
  {
    slug: 'accountant',
    name: 'Бухгалтер',
    published: '2026-10-07',
    readingMinutes: readingMinutes(accountant.article),
    teaser:
      'Узнай, о чём спрашивают бухгалтера и главбуха, и подготовь ответы про первичку, НДС и зарплату',
    title:
      'Вопросы на собеседовании бухгалтера и главного бухгалтера с ответами | Workbit',
    description:
      'Какие вопросы задают на собеседовании бухгалтеру и главному бухгалтеру и что хотят услышать: первичка, проводки, НДС 22%, зарплата и тесты.',
    load: async () => accountant,
  },
]
