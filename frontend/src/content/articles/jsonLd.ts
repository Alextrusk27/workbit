import type { Article, ArticleEntry } from '@/content/articles/types'

function plain(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, '$1')
}

export function articleJsonLd(
  site: string,
  url: string,
  entry: ArticleEntry,
  article: Article,
): object[] {
  const { lead, accent } = article.hero
  const organization = {
    '@type': 'Organization',
    name: 'Workbit',
    url: site,
  }
  const result: object[] = [
    {
      '@context': 'https://schema.org',
      '@type': 'Article',
      headline: accent ? `${lead} ${accent}` : lead,
      description: entry.description,
      datePublished: entry.published,
      inLanguage: 'ru',
      url,
      mainEntityOfPage: { '@type': 'WebPage', '@id': url },
      image: `${site}/og-image.png`,
      author: organization,
      publisher: { ...organization, logo: `${site}/favicon.png` },
    },
  ]

  const questions = article.sections.flatMap((s) =>
    s.blocks.flatMap((b) =>
      typeof b === 'object' && b.type === 'qa' ? b.items : [],
    ),
  )
  if (questions.length) {
    result.push({
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: questions.map((item) => ({
        '@type': 'Question',
        name: plain(item.q),
        acceptedAnswer: { '@type': 'Answer', text: plain(item.what) },
      })),
    })
  }
  return result
}
