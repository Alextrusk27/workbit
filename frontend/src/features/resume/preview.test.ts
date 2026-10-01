import { describe, expect, it } from 'vitest'
import { charsetOf, decodeText } from './preview'

const PRIVET_1251 = new Uint8Array([0xcf, 0xf0, 0xe8, 0xe2, 0xe5, 0xf2])

describe('charsetOf', () => {
  it('берёт charset из Content-Type', () => {
    expect(charsetOf('text/plain;charset=windows-1251')).toBe('windows-1251')
    expect(charsetOf('text/plain; charset="UTF-8"')).toBe('UTF-8')
  })

  it('без charset возвращает utf-8', () => {
    expect(charsetOf('text/plain')).toBe('utf-8')
    expect(charsetOf('')).toBe('utf-8')
  })
})

describe('decodeText', () => {
  it('декодирует windows-1251 по charset из типа blob', async () => {
    const blob = new Blob([PRIVET_1251], {
      type: 'text/plain;charset=windows-1251',
    })
    expect(await decodeText(blob)).toBe('Привет')
  })

  it('декодирует UTF-8 и отбрасывает BOM', async () => {
    const bytes = new TextEncoder().encode('﻿Привет')
    const blob = new Blob([bytes], { type: 'text/plain;charset=UTF-8' })
    expect(await decodeText(blob)).toBe('Привет')
  })
})
