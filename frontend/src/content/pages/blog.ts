import type { Hero, PageSeo } from '@/content/types'

export interface BlogContent {
  hero: Hero & { text: string }
  all: string
  empty: string
}

export const seo: PageSeo = {
  title: 'Блог о собеседованиях: вопросы по профессиям и новости | Workbit',
  description:
    'Какие вопросы задают на собеседовании по профессиям и что хотят услышать в ответ, плюс новости тренажёра собеседований Workbit.',
}

export const blog: BlogContent = {
  hero: {
    lead: 'Блог',
    accent: 'о собеседованиях',
    text: 'Какие вопросы задают по профессиям, что хотят услышать в ответ и что нового в тренажёре.',
  },
  all: 'Все',
  empty: 'В этой рубрике пока пусто — статьи появятся скоро.',
}
