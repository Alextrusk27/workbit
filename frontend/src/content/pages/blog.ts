import type { Hero, PageSeo } from '@/content/types'

export interface BlogContent {
  hero: Hero & { text: string }
  all: string
  back: string
  author: string
  empty: string
  minutes: string
  sources: string
  article: {
    toc: string
    readTime: string
    why: string
    what: string
    mistake: string
    tip: string
    expand: string
    collapse: string
  }
  search: {
    label: string
    placeholder: string
    emptyTitle: string
    emptyText: string
  }
  view: { label: string; list: string; tiles: string }
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
  empty: 'В этой рубрике пока пусто, статьи появятся скоро.',
  minutes: 'мин',
  sources: 'Источники',
  article: {
    toc: 'Содержание',
    readTime: 'мин чтения',
    why: 'Зачем спрашивают',
    what: 'Что хотят услышать',
    mistake: 'Частая ошибка',
    tip: 'Совет',
    expand: 'Раскрыть все',
    collapse: 'Свернуть все',
  },
  search: {
    label: 'Поиск по профессии',
    placeholder: 'Найти профессию',
    emptyTitle: 'Такой профессии пока нет',
    emptyText: 'Попробуй другое название или посмотри все статьи.',
  },
  view: { label: 'Вид списка', list: 'Списком', tiles: 'Плитками' },
}
