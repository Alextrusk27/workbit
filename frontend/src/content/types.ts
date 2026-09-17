export interface PageSeo {
  title: string
  description: string
}

export interface Hero {
  lead: string
  accent?: string
  breakBeforeAccent?: boolean
}

export interface Training {
  skill: string
  level: 'EASY' | 'MEDIUM' | 'HARD'
  profession?: string
}

export type Cta =
  | { label: string; to: string }
  | { label: string; start: true }
  | { label: string; train: Training }
