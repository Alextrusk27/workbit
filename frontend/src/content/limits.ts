export interface Pack {
  name: string
  price: string
  /** Перечёркнутая цена до скидки — показывается вместе с плашкой discount. */
  oldPrice?: string
  /** Текст плашки скидки, например «−30%». */
  discount?: string
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
    note: 'Один раз на вопрос, повторный просмотр бесплатен. Доступен после первого пополнения',
  },
]

/** Зеркало `TopUpPricing` на бэке: границы, шаг и ступени цены за лимит. */
export const TOPUP = {
  min: 50,
  max: 5000,
  sliderMax: 500,
  step: 10,
  presets: [50, 100, 200, 300, 500],
  tiers: [
    { from: 50, price: 15 },
    { from: 100, price: 13.5 },
    { from: 200, price: 12 },
    { from: 300, price: 11 },
    { from: 500, price: 10 },
  ],
} as const

export interface TopUpQuote {
  limits: number
  perLimit: number
  amount: number
  /** Скидка от базовой цены в процентах, 0 на первой ступени. */
  discount: number
}

export function normalizeLimits(value: number): number {
  if (!Number.isFinite(value)) return TOPUP.min
  const clamped = Math.min(TOPUP.max, Math.max(TOPUP.min, value))
  return Math.round(clamped / TOPUP.step) * TOPUP.step
}

export function topUpQuote(limits: number): TopUpQuote {
  const base = TOPUP.tiers[0].price
  const tier = [...TOPUP.tiers].reverse().find((t) => limits >= t.from)!
  return {
    limits,
    perLimit: tier.price,
    amount: Math.floor(limits * tier.price),
    discount: Math.round((1 - tier.price / base) * 100),
  }
}

export const freePack: Pack = {
  name: 'Бесплатно',
  price: '0 ₽',
  period: '',
  cta: 'Начать бесплатно',
  featured: false,
  features: [
    `${WELCOME_LIMITS} лимитов при регистрации`,
    'Хватит на AI-интервью или две тренировки',
    'Полный разбор: оценки и правки на полях',
    'Эталонные ответы — после первого пополнения',
  ],
  previewFeatures: [
    `${WELCOME_LIMITS} лимитов при регистрации`,
    'AI-интервью или две тренировки',
    'Полный разбор ответов',
  ],
}

export const topUp = {
  name: 'Пополнение баланса',
  lead: 'Сколько нужно — столько и покупаешь. Чем больше, тем дешевле лимит.',
  cta: 'Пополнить',
}
