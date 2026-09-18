import { plans } from '@/content/plans'
import type { Cta, Hero, PageSeo } from '@/content/types'

export interface PricingContent {
  hero: Hero
  note: string
  cta: { title: string; body: string; primary: Cta }
}

export const seo: PageSeo = {
  title: 'Сколько стоит подготовка к собеседованию | Workbit',
  description: `${plans
    .map((p) => `${p.name} — ${p.period ? `${p.price} в месяц` : p.price}`)
    .join(
      ', ',
    )} — тарифы тренажёра собеседований. Разовый платёж на 30 дней, без автосписаний и привязки карты.`,
}

export const pricing: PricingContent = {
  hero: { lead: 'Сколько стоит', accent: 'подготовка к собеседованию' },
  note: 'Тариф действует 30 дней с момента оплаты. Не хватило лимита — оплати тариф ещё раз: срок продлится, а лимиты добавятся к оставшимся. Условия оплаты определяет [Публичная оферта](/offer).',
  cta: {
    title: 'Остались вопросы?',
    body: 'Загляни в FAQ — там коротко о формате, профессиях и оценке ответов.',
    primary: { label: 'Открыть FAQ', to: '/faq' },
  },
}
