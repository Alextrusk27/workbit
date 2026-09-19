import type { PaymentProduct } from '@/features/billing/api'

export interface Pack {
  name: string
  /** Код продукта на бэке — есть только у платных пакетов. */
  product?: PaymentProduct
  price: string
  /** Перечёркнутая цена до скидки — показывается вместе с плашкой discount. */
  oldPrice?: string
  /** Текст плашки скидки, например «−30%». */
  discount?: string
  /** Подарок-промо — заметная плашка на карточке. */
  promo?: string
  period: string
  cta: string
  featured: boolean
  /** Полный список — страница тарифов. */
  features: string[]
  /** Короткий список — превью на главной. */
  previewFeatures: string[]
}

export const OPERATION_COST = {
  interview: 20,
  training: 10,
  more: 10,
  reference: 1,
} as const

export const WELCOME_LIMITS = 20

export const operations: { name: string; cost: number; note: string }[] = [
  {
    name: 'AI-интервью по вакансии',
    cost: OPERATION_COST.interview,
    note: 'Списывается при старте, голосом или текстом — одинаково',
  },
  {
    name: 'Тренировка навыка',
    cost: OPERATION_COST.training,
    note: '10 вопросов и разбор; перезапуск стоит столько же',
  },
  {
    name: 'Ещё 10 вопросов в тренировке',
    cost: OPERATION_COST.more,
    note: 'До 50 вопросов в одной тренировке',
  },
  {
    name: 'Эталонный ответ',
    cost: OPERATION_COST.reference,
    note: 'Один раз на вопрос, повторный просмотр бесплатен. Доступен после первой покупки',
  },
]

/** Акция «+10 лимитов в подарок» — по 31 октября 2026 включительно. */
export const promo = {
  active: __BUILD_TS__ < Date.parse('2026-11-01T00:00:00+03:00'),
  card: '+10 лимитов в подарок до 1 ноября',
  pricing: 'До 1 ноября к любому пакету — +10 лимитов в подарок.',
  home: 'До 1 ноября к любому пакету — +10 лимитов в подарок.',
}

export const packs: Pack[] = [
  {
    name: 'Бесплатно',
    price: '0 ₽',
    period: '',
    cta: 'Начать бесплатно',
    featured: false,
    features: [
      `${WELCOME_LIMITS} лимитов при регистрации`,
      'Хватит на AI-интервью или две тренировки',
      'Полный разбор: оценки и правки на полях',
      'Эталонные ответы — после первой покупки',
    ],
    previewFeatures: [
      `${WELCOME_LIMITS} лимитов при регистрации`,
      'AI-интервью или две тренировки',
      'Полный разбор ответов',
    ],
  },
  {
    name: '50 лимитов',
    product: 'PACK_50',
    price: '690 ₽',
    period: '',
    cta: 'Купить 50 лимитов',
    featured: false,
    promo: promo.active ? promo.card : undefined,
    features: [
      'Хватит на 1 интервью, 2 тренировки и 10 эталонных ответов',
      'Действуют 3 месяца',
      'Глубокие тренировки — до 50 вопросов',
      'Эталонные ответы к вопросам',
    ],
    previewFeatures: [
      '1 интервью, 2 тренировки и 10 эталонов',
      'Действуют 3 месяца',
      'Глубокие тренировки — до 50 вопросов',
    ],
  },
  {
    name: '200 лимитов',
    product: 'PACK_200',
    price: '2290 ₽',
    period: '',
    cta: 'Купить 200 лимитов',
    featured: true,
    promo: promo.active ? promo.card : undefined,
    features: [
      'Хватит на 4 интервью, 10 тренировок и 20 эталонных ответов',
      'Действуют 3 месяца',
      'Глубокие тренировки — до 50 вопросов',
      'Эталонные ответы к вопросам',
      'Повторные прохождения и динамика по вакансии',
    ],
    previewFeatures: [
      '4 интервью, 10 тренировок и 20 эталонов',
      'Действуют 3 месяца',
      'Глубокие тренировки — до 50 вопросов',
      'Динамика по вакансии',
    ],
  },
  {
    name: '500 лимитов',
    product: 'PACK_500',
    price: '4990 ₽',
    period: '',
    cta: 'Купить 500 лимитов',
    featured: false,
    promo: promo.active ? promo.card : undefined,
    features: [
      'Хватит на 10 интервью, 25 тренировок и 50 эталонных ответов',
      'Действуют 3 месяца',
      'Всё остальное — как в пакете 200',
    ],
    previewFeatures: [
      '10 интервью, 25 тренировок и 50 эталонов',
      'Действуют 3 месяца',
      'Всё остальное — как в пакете 200',
    ],
  },
]
