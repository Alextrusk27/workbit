import type { Cta, Hero, PageSeo } from '@/content/types'

export type ArticleBlock =
  | string
  | { type: 'list'; ordered?: boolean; items: string[] }
  | { type: 'qa'; items: QaItem[] }
  | { type: 'table'; caption?: string; head: string[]; rows: string[][] }
  | { type: 'tip'; text: string }
  | { type: 'cta'; text: string; action: Cta }

export interface QaItem {
  q: string
  why: string
  what: string
  mistake?: string
}

export interface ArticleSection {
  id: string
  title: string
  blocks: ArticleBlock[]
}

export interface Article {
  hero: Hero
  intro?: string
  sections: ArticleSection[]
  sources?: string[]
  cta: {
    title: string
    body: string
    primary: Cta
    secondary?: Cta
    note?: string
  }
}

export interface ArticleEntry extends PageSeo {
  slug: string
  name: string
  published: string
  readingMinutes: number
  teaser: string
  load: () => Promise<{ article: Article }>
}

export interface Rubric {
  label: string
  entries: ArticleEntry[]
}
