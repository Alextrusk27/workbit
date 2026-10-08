import { describe, expect, it } from 'vitest'
import type { Article } from './types'
import { readingMinutes } from './readingMinutes'

const words = (n: number) => Array.from({ length: n }, () => 'слово').join(' ')

function article(overrides: Partial<Article> = {}): Article {
  return {
    hero: { lead: '' },
    sections: [],
    cta: { title: '', body: '', primary: { label: 'Начать', start: true } },
    ...overrides,
  }
}

describe('readingMinutes', () => {
  it('пустая статья читается минимум за минуту', () => {
    expect(readingMinutes(article())).toBe(1)
  })

  it('180 слов - одна минута, 181 - уже две', () => {
    expect(readingMinutes(article({ intro: words(180) }))).toBe(1)
    expect(readingMinutes(article({ intro: words(181) }))).toBe(2)
  })

  it('считает слова во всех частях статьи и типах блоков', () => {
    const a = article({
      hero: { lead: words(10), accent: words(10) },
      intro: words(10),
      sections: [
        {
          id: 's',
          title: words(10),
          blocks: [
            words(100),
            { type: 'list', items: [words(50), words(50)] },
            {
              type: 'qa',
              items: [{ q: words(10), why: words(50), what: words(50) }],
            },
            {
              type: 'cta',
              text: words(10),
              action: { label: 'Начать', start: true },
            },
          ],
        },
      ],
      cta: {
        title: words(5),
        body: words(5),
        note: words(1),
        primary: { label: 'Начать', start: true },
      },
    })
    expect(readingMinutes(a)).toBe(3)
  })

  it('считает слова в таблицах, советах и частых ошибках', () => {
    const a = article({
      sections: [
        {
          id: 's',
          title: '',
          blocks: [
            {
              type: 'table',
              caption: words(20),
              head: [words(10), words(10)],
              rows: [[words(30), words(30)]],
            },
            { type: 'tip', text: words(40) },
            {
              type: 'qa',
              items: [{ q: '', why: '', what: '', mistake: words(41) }],
            },
          ],
        },
      ],
    })
    expect(readingMinutes(a)).toBe(2)
  })

  it('не считает словами знаки препинания и стрелки', () => {
    expect(readingMinutes(article({ intro: `${words(180)} → — ...` }))).toBe(1)
  })
})
