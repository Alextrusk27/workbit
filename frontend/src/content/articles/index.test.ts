import { describe, expect, it } from 'vitest'
import { articles, findArticle } from './index'

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/

describe('реестр статей', () => {
  it('рубрики и слаги латиницей в нижнем регистре через дефис, без дублей', () => {
    for (const [rubric, entries] of Object.entries(articles)) {
      expect(rubric).toMatch(SLUG)
      const slugs = entries.map((a) => a.slug)
      expect(new Set(slugs).size).toBe(slugs.length)
      for (const slug of slugs) expect(slug).toMatch(SLUG)
    }
  })

  it('каждая запись загружает тело статьи', async () => {
    for (const entries of Object.values(articles)) {
      for (const entry of entries) {
        const { article } = await entry.load()
        expect(article.hero.lead).toBeTruthy()
      }
    }
  })

  it('неизвестные рубрика или слаг дают undefined', () => {
    expect(findArticle('interview-questions', 'sales-manager')).toBeDefined()
    expect(findArticle('guides', 'sales-manager')).toBeUndefined()
    expect(findArticle('interview-questions', 'nope')).toBeUndefined()
    expect(findArticle()).toBeUndefined()
  })
})
