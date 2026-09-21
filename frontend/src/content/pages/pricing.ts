import { TOPUP } from '@/content/limits'
import type { Cta, Hero, PageSeo } from '@/content/types'

export interface PricingContent {
  hero: Hero
  operations: { title: string; lead: string }
  note: string
  cta: { title: string; body: string; primary: Cta }
}

export const seo: PageSeo = {
  title: 'Сколько стоит подготовка к собеседованию | Workbit',
  description: `Пополнение от ${TOPUP.min} лимитов: ${TOPUP.tiers[0].price} ₽ за лимит, от ${TOPUP.tiers[TOPUP.tiers.length - 1].from} — ${TOPUP.tiers[TOPUP.tiers.length - 1].price} ₽. Лимиты тренажёра собеседований действуют 3 месяца, без автосписаний и привязки карты.`,
}

export const pricing: PricingContent = {
  hero: { lead: 'Сколько стоит', accent: 'подготовка к собеседованию' },
  operations: {
    title: 'Что сколько стоит',
    lead: 'Один баланс на всё: интервью, тренировки и эталонные ответы списывают лимиты по единому прайсу.',
  },
  note: 'Лимиты действуют 3 месяца с момента последней покупки. Условия оплаты определяет [Публичная оферта](/offer).',
  cta: {
    title: 'Остались вопросы?',
    body: 'Загляни в FAQ — там коротко о формате, профессиях и оценке ответов.',
    primary: { label: 'Открыть FAQ', to: '/faq' },
  },
}
