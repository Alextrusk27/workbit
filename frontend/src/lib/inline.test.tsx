import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { inline } from './inline'

const html = (text: string) =>
  renderToStaticMarkup(<MemoryRouter>{inline(text)}</MemoryRouter>)

const STRONG = '<strong class="text-ink font-semibold">'
const LINK = 'class="text-indigo hover:text-violet transition-colors"'
const ROUTER_LINK = (to: string) =>
  `<a ${LINK} href="${to}" data-discover="true">`

describe('inline', () => {
  it('обычный текст отдаёт как есть', () => {
    expect(html('Привет, мир')).toBe('Привет, мир')
  })

  it('**выделение** превращает в strong', () => {
    expect(html('Ответы **текстом** или **голосом**')).toBe(
      `Ответы ${STRONG}текстом</strong> или ${STRONG}голосом</strong>`,
    )
  })

  it('ссылку с ведущим / превращает в Link', () => {
    expect(html('Смотри [тарифы](/pricing).')).toBe(
      `Смотри ${ROUTER_LINK('/pricing')}тарифы</a>.`,
    )
  })

  it('mailto и https превращает в обычную ссылку', () => {
    expect(html('[пиши](mailto:support@workbit.ru)')).toBe(
      `<a href="mailto:support@workbit.ru" ${LINK}>пиши</a>`,
    )
    expect(html('[hh.ru](https://hh.ru/)')).toBe(
      `<a href="https://hh.ru/" ${LINK}>hh.ru</a>`,
    )
  })

  it('перенос строки превращает в br', () => {
    expect(html('Первая\nвторая')).toBe('Первая<br/>вторая')
  })

  it('не трогает одиночные звёздочки и скобки', () => {
    expect(html('5 * 2 [шт] (примерно)')).toBe('5 * 2 [шт] (примерно)')
  })

  it('оставляет пробелы вокруг разметки', () => {
    expect(html('**Что проверяют** — и [зачем](/faq) это нужно')).toBe(
      `${STRONG}Что проверяют</strong> — и ${ROUTER_LINK('/faq')}зачем</a> это нужно`,
    )
  })

  it('пустую строку отдаёт как null, чтобы guard на children её отсекал', () => {
    expect(inline('')).toBeNull()
  })
})
