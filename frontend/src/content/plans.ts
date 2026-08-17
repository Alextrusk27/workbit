export interface Plan {
  name: string
  /** Код продукта на бэке — есть только у платных тарифов. */
  product?: 'PLAN_PRO' | 'PLAN_MAX'
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

/** Акция «интервью в подарок» — по 30 сентября 2026 включительно. */
export const promoActive = Date.now() < Date.parse('2026-10-01T00:00:00+03:00')

export const plans: Plan[] = [
  {
    name: 'Старт',
    price: '0 ₽',
    period: '',
    cta: 'Начать бесплатно',
    featured: false,
    features: [
      '3 тренировки',
      '1 AI-интервью с голосом',
      'Полный разбор: оценки и правки на полях',
      'Эталонные ответы к вопросам',
    ],
    previewFeatures: ['3 тренировки', '1 AI-интервью', 'Полный разбор ответов'],
  },
  {
    name: 'Про',
    product: 'PLAN_PRO',
    price: '790 ₽',
    period: '/ месяц',
    cta: 'Перейти на Про',
    featured: true,
    promo: promoActive ? '+2 интервью в подарок до 1 октября' : undefined,
    features: [
      '10 AI-интервью в месяц',
      '20 тренировок в месяц',
      'Глубокие тренировки — до 50 вопросов',
      'Повторные прохождения и динамика по вакансии',
    ],
    previewFeatures: [
      '10 AI-интервью в месяц',
      '20 тренировок в месяц',
      'Глубокие тренировки — до 50 вопросов',
      'Динамика по вакансии',
    ],
  },
  {
    name: 'Макс',
    product: 'PLAN_MAX',
    price: '1490 ₽',
    period: '/ месяц',
    cta: 'Перейти на Макс',
    featured: false,
    promo: promoActive ? '+5 интервью в подарок до 1 октября' : undefined,
    features: [
      '25 AI-интервью в месяц',
      'Безлимит тренировок',
      'Всё остальное — как в Про',
    ],
    previewFeatures: [
      '25 AI-интервью в месяц',
      'Безлимит тренировок',
      'Всё остальное — как в Про',
    ],
  },
]
