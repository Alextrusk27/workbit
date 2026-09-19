import { packs } from '@/content/packs'
import type { PaymentProduct } from './api'

/** Цена продукта в рублях — из карточек пакетов (`content/packs.ts`),
 *  чтобы сумма для аналитики не разъезжалась с витриной. */
export function productPrice(product: PaymentProduct): number | undefined {
  const price = packs.find((p) => p.product === product)?.price
  const value = price ? parseInt(price, 10) : NaN
  return Number.isFinite(value) ? value : undefined
}
