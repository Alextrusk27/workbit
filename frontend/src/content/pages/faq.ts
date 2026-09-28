import type { Hero, PageSeo } from '@/content/types'

export interface FaqContent {
  hero: Hero & { text: string }
  cta: {
    title: string
    body: string
    email: string
    copy: string
    copied: string
  }
}

export const seo: PageSeo = {
  title: 'Частые вопросы о тренажёре собеседований | Workbit',
  description:
    'Как работает ИИ-интервью, по каким профессиям есть вопросы, как ИИ оценивает ответы и как устроена оплата. Короткие ответы на частые вопросы.',
}

export const faq: FaqContent = {
  hero: {
    lead: 'Частые вопросы',
    accent: 'о тренажёре собеседований',
    text: 'Коротко о формате, профессиях, оценке ответов и оплате.',
  },
  cta: {
    title: 'Остались вопросы?',
    body: 'Не нашёл ответ? Напиши нам на [support@workbit.ru](mailto:support@workbit.ru).',
    email: 'support@workbit.ru',
    copy: 'Скопировать адрес',
    copied: 'Адрес скопирован',
  },
}
