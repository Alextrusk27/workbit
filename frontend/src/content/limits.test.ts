import { describe, expect, it } from 'vitest'
import { TOPUP, topUpQuote } from './limits'

describe('topUpQuote', () => {
  it.each([
    [10, 150],
    [50, 750],
    [100, 1350],
    [180, 2190],
    [200, 2400],
    [280, 3120],
    [300, 3300],
    [400, 4200],
    [460, 4680],
    [500, 5000],
  ])('%i лимитов стоят %i ₽', (limits, amount) => {
    expect(topUpQuote(limits).amount).toBe(amount)
  })

  it('больше лимитов никогда не стоит дешевле', () => {
    for (let n = TOPUP.min + TOPUP.step; n <= TOPUP.max; n += TOPUP.step) {
      expect(topUpQuote(n).amount).toBeGreaterThanOrEqual(
        topUpQuote(n - TOPUP.step).amount,
      )
    }
  })
})
