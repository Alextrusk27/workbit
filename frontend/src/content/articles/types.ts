import type { Cta, Hero, PageSeo } from '@/content/types'

export type ArticleBlock =
  | string
  | { type: 'list'; ordered?: boolean; items: string[] }
  | { type: 'qa'; items: { q: string; why: string; what: string }[] }
  | { type: 'cta'; text: string; action: Cta }

export interface ArticleSection {
  id: string
  title: string
  blocks: ArticleBlock[]
}

export interface Article {
  hero: Hero
  intro: string
  sections: ArticleSection[]
  cta: {
    title: string
    body: string
    primary: Cta
    secondary?: Cta
    note: string
  }
}

export interface ArticleEntry extends PageSeo {
  slug: string
  name: string
  load: () => Promise<{ article: Article }>
}

export interface Rubric {
  label: string
  entries: ArticleEntry[]
}
