import { packs } from '@/content/packs'
import type { Cta, Hero, PageSeo } from '@/content/types'

export interface PricingContent {
  hero: Hero
  operations: { title: string; lead: string }
  note: string
  cta: { title: string; body: string; primary: Cta }
}

export const seo: PageSeo = {
  title: 'Сколько стоит подготовка к собеседованию | Workbit',
  description: `${packs
    .filter((p) => p.product)
    .map((p) => `${p.name} — ${p.price}`)
    .join(
      ', ',
    )} — пакеты лимитов тренажёра собеседований. Лимиты действуют 3 месяца, без автосписаний и привязки карты.`,
}

export const pricing: PricingContent = {
  hero: { lead: 'Сколько стоит', accent: 'подготовка к собеседованию' },
  operations: {
    title: 'Что сколько стоит',
    lead: 'Один баланс на всё: интервью, тренировки и эталонные ответы списывают лимиты по единому прайсу.',
  },
  note: 'Лимиты действуют 3 месяца с момента покупки. Докупил пакет — срок всего баланса продлевается ещё на 3 месяца, а новые лимиты добавляются к оставшимся. Условия оплаты определяет [Публичная оферта](/offer).',
  cta: {
    title: 'Остались вопросы?',
    body: 'Загляни в FAQ — там коротко о формате, профессиях и оценке ответов.',
    primary: { label: 'Открыть FAQ', to: '/faq' },
  },
}
