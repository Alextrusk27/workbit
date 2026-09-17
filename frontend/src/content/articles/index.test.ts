import { describe, expect, it } from 'vitest'
import { findArticle, rubrics } from './index'

const SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/

describe('реестр статей', () => {
  it('рубрики и слаги латиницей в нижнем регистре через дефис, без дублей', () => {
    for (const [rubric, { entries }] of Object.entries(rubrics)) {
      expect(rubric).toMatch(SLUG)
      const slugs = entries.map((a) => a.slug)
      expect(new Set(slugs).size).toBe(slugs.length)
      for (const slug of slugs) expect(slug).toMatch(SLUG)
    }
  })

  it('каждая запись загружает тело статьи', async () => {
    for (const { entries } of Object.values(rubrics)) {
      for (const entry of entries) {
        const { article } = await entry.load()
        expect(article.hero.lead).toBeTruthy()
      }
    }
  })

  it('неизвестные рубрика или слаг дают undefined', () => {
    expect(findArticle('interview-questions', 'sales-manager')).toBeDefined()
    expect(findArticle('news', 'sales-manager')).toBeUndefined()
    expect(findArticle('interview-questions', 'nope')).toBeUndefined()
    expect(findArticle()).toBeUndefined()
  })
})
