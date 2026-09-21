export interface Pack {
  name: string
  price: string
  cta: string
  features: string[]
}

export const OPERATION_COST = {
  interview: 20,
  training: 10,
  more: 10,
  reference: 1,
} as const

export const WELCOME_LIMITS = 20

export const operations: { name: string; cost: number }[] = [
  { name: 'AI-интервью по вакансии', cost: OPERATION_COST.interview },
  { name: 'Тренировка навыка, 10 вопросов', cost: OPERATION_COST.training },
]

export const TOPUP = {
  min: 10,
  max: 500,
  step: 10,
  tiers: [
    { from: 10, price: 15 },
    { from: 100, price: 13.5 },
    { from: 200, price: 12 },
    { from: 300, price: 11 },
    { from: 500, price: 10 },
  ],
} as const

export interface TopUpQuote {
  amount: number
  saving: number
}

export function normalizeLimits(value: number): number {
  if (!Number.isFinite(value)) return TOPUP.min
  const clamped = Math.min(TOPUP.max, Math.max(TOPUP.min, value))
  return Math.round(clamped / TOPUP.step) * TOPUP.step
}

export function topUpQuote(limits: number): TopUpQuote {
  const tier = [...TOPUP.tiers].reverse().find((t) => limits >= t.from)!
  const amount = Math.floor(limits * tier.price)
  return { amount, saving: limits * TOPUP.tiers[0].price - amount }
}

export const freePack: Pack = {
  name: 'Бесплатно',
  price: '0 ₽',
  cta: 'Начать бесплатно',
  features: [
    `${WELCOME_LIMITS} лимитов при регистрации`,
    'AI-интервью или две тренировки',
    'Полный разбор ответов',
  ],
}

export const topUp = {
  name: 'Пополнение баланса',
  cta: 'Пополнить',
}
