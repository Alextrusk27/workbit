import { plans } from '@/content/plans'
import type { PaymentProduct, Plan } from './api'

export const PLAN_LABELS: Record<Plan, string> = {
  FREE: 'Старт',
  PRO: 'Про',
  MAX: 'Макс',
}

/** Цена продукта в рублях — из карточек тарифов (`content/plans.ts`),
 *  чтобы сумма для аналитики не разъезжалась с витриной. */
export function productPrice(product: PaymentProduct): number | undefined {
  const price = plans.find((p) => p.product === product)?.price
  const value = price ? parseInt(price, 10) : NaN
  return Number.isFinite(value) ? value : undefined
}
