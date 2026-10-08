import { readingMinutes } from '@/content/articles/readingMinutes'
import type { ArticleEntry } from '@/content/articles/types'
import * as accountant from './accountant'
import * as salesManager from './sales-manager'

export const interviewQuestions: ArticleEntry[] = [
  {
    slug: 'sales-manager',
    name: 'Менеджер по продажам',
    published: '2026-10-07',
    readingMinutes: readingMinutes(salesManager.article),
    teaser:
      'Узнай, о чём спрашивают менеджера по продажам, и подготовь ответы про план, возражения и «продай мне ручку»',
    title:
      'Вопросы на собеседовании менеджера по продажам и «продай ручку» | Workbit',
    description:
      'Что спрашивают на собеседовании менеджера по продажам и что хотят услышать: план и сделки, возражения, ролевая игра, «продай мне ручку» и разговор о деньгах.',
    load: async () => salesManager,
  },
  {
    slug: 'accountant',
    name: 'Бухгалтер',
    published: '2026-10-08',
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
