import type { Hero, PageSeo } from '@/content/types'

export interface BlogContent {
  hero: Hero & { text: string }
  all: string
  back: string
  author: string
  empty: string
}

export const seo: PageSeo = {
  title: 'Блог о собеседованиях: вопросы по профессиям | Workbit',
  description:
    'Какие вопросы задают на собеседовании по профессиям, зачем их задают и что хотят услышать в ответ. Разборы от тренажёра собеседований Workbit.',
}

export const blog: BlogContent = {
  hero: {
    lead: 'Блог',
    accent: 'о собеседованиях',
    text: 'Какие вопросы задают по профессиям, зачем их задают и что хотят услышать в ответ.',
  },
  all: 'Все',
  back: 'Блог',
  author: 'Команда Workbit',
  empty: 'В этой рубрике пока пусто — статьи появятся скоро.',
}
