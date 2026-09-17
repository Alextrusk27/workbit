import { describe, expect, it } from 'vitest'
import { ctaLink } from './cta'

const guest = { start: '/app/training/new', isAuthenticated: false }
const user = { ...guest, isAuthenticated: true }

describe('ctaLink', () => {
  it('обычный путь отдаёт как есть, без guard', () => {
    expect(ctaLink({ label: 'FAQ', to: '/faq' }, guest)).toEqual({ to: '/faq' })
  })

  it('start ведёт на стартовый путь страницы', () => {
    expect(ctaLink({ label: 'Начать', start: true }, user)).toEqual({
      to: '/app/training/new',
    })
  })

  it('train собирает ссылку в мастер тренировки', () => {
    expect(
      ctaLink(
        { label: 'Тренировать', train: { skill: 'React', level: 'EASY' } },
        user,
      ),
    ).toEqual({ to: '/app/training/new?skill=React&level=EASY' })
  })

  it('гостя ведёт на /login с возвратом в цель', () => {
    expect(ctaLink({ label: 'Начать', start: true }, guest)).toEqual({
      to: '/login',
      state: { from: { pathname: '/app/training/new' } },
    })
  })
})
