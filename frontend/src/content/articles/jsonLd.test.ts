import { describe, expect, it } from 'vitest'
import { articleJsonLd } from './jsonLd'
import type { Article, ArticleEntry } from './types'

const SITE = 'https://example.com'
const URL = `${SITE}/blog/r/s`

const entry: ArticleEntry = {
  slug: 's',
  name: 'Статья',
  published: '2026-10-01',
  readingMinutes: 1,
  teaser: 'Анонс статьи',
  title: 'Заголовок | Workbit',
  description: 'Описание статьи',
  load: async () => ({ article }),
}

const article: Article = {
  hero: { lead: 'Вопросы', accent: 'бухгалтеру' },
  sections: [
    {
      id: 'a',
      title: 'Первая',
      blocks: [
        'Абзац',
        {
          type: 'qa',
          items: [
            {
              q: 'Что такое **баланс**?',
              why: 'Проверяют базу',
              what: 'Отчёт, см. [статью](/blog/r/x) и **актив**',
            },
          ],
        },
      ],
    },
    {
      id: 'b',
      title: 'Вторая',
      blocks: [
        {
          type: 'qa',
          items: [{ q: 'Второй вопрос', why: 'Зачем', what: 'Ответ' }],
        },
      ],
    },
  ],
  cta: { title: 'CTA', body: 'Текст', primary: { label: 'Начать', to: '/' } },
}

describe('articleJsonLd', () => {
  it('Article берёт headline из lead и accent, остальное из записи', () => {
    const [ld] = articleJsonLd(SITE, URL, entry, article)
    expect(ld).toMatchObject({
      '@type': 'Article',
      headline: 'Вопросы бухгалтеру',
      description: 'Описание статьи',
      datePublished: '2026-10-01',
      url: URL,
      image: `${SITE}/og-image.png`,
      publisher: { name: 'Workbit', logo: `${SITE}/favicon.png` },
    })
  })

  it('FAQPage собирает вопросы из всех секций и снимает разметку', () => {
    const [, faq] = articleJsonLd(SITE, URL, entry, article)
    expect(faq).toEqual({
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: [
        {
          '@type': 'Question',
          name: 'Что такое баланс?',
          acceptedAnswer: {
            '@type': 'Answer',
            text: 'Отчёт, см. статью и актив',
          },
        },
        {
          '@type': 'Question',
          name: 'Второй вопрос',
          acceptedAnswer: { '@type': 'Answer', text: 'Ответ' },
        },
      ],
    })
  })

  it('без блоков qa возвращает только Article', () => {
    const noQa: Article = {
      ...article,
      hero: { lead: 'Только lead' },
      sections: [{ id: 'a', title: 'Секция', blocks: ['Абзац'] }],
    }
    const result = articleJsonLd(SITE, URL, entry, noQa)
    expect(result).toHaveLength(1)
    expect(result[0]).toMatchObject({
      '@type': 'Article',
      headline: 'Только lead',
    })
  })
})
