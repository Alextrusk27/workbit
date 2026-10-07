import type { Article, ArticleBlock } from '@/content/articles/types'

const WORDS_PER_MINUTE = 180

function blockText(block: ArticleBlock): string[] {
  if (typeof block === 'string') return [block]
  switch (block.type) {
    case 'list':
      return block.items
    case 'qa':
      return block.items.flatMap(({ q, why, what }) => [q, why, what])
    case 'cta':
      return [block.text]
  }
}

export function readingMinutes(article: Article): number {
  const text = [
    article.hero.lead,
    article.hero.accent ?? '',
    article.intro ?? '',
    ...article.sections.flatMap((s) => [
      s.title,
      ...s.blocks.flatMap(blockText),
    ]),
    article.cta.title,
    article.cta.body,
    article.cta.note ?? '',
  ].join(' ')
  const words = text.split(/\s+/).filter((w) => /[\p{L}\p{N}]/u.test(w)).length
  return Math.max(1, Math.ceil(words / WORDS_PER_MINUTE))
}
