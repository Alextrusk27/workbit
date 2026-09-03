/** Яндекс Метрика: id счётчика и отправка целей. Сниппет init живёт в
 *  `index.html`, хиты SPA-переходов шлёт `RootLayout`. Каждая цель отсюда
 *  должна быть заведена в кабинете Метрики как «JavaScript-событие»
 *  с тем же идентификатором — иначе событие не попадёт в отчёты. */

declare global {
  interface Window {
    ym?: (id: number, action: string, ...params: unknown[]) => void
  }
}

export const METRIKA_ID = 111653697

/** Единый список идентификаторов целей — чтобы код не разъезжался с кабинетом. */
export type MetrikaGoal =
  | 'registration'
  | 'interview_start'
  | 'interview_finish'
  | 'training_start'
  | 'training_finish'
  | 'payment_success'

/** Отправка цели. Для `payment_success` параметры `order_price` + `currency`
 *  Метрика понимает как доход цели (передача ценности в отчёты). */
export function reachGoal(
  goal: MetrikaGoal,
  params?: { order_price?: number; currency?: 'RUB' },
) {
  if (typeof window === 'undefined') return
  window.ym?.(METRIKA_ID, 'reachGoal', goal, params)
}
