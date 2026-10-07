import { useParams } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { ArticleTiles } from '@/components/marketing/ArticleTiles'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { findRubric } from '@/content/articles'
import { blog } from '@/content/pages/blog'
import { inline } from '@/lib/inline'
import { NotFoundPage } from '@/pages/NotFoundPage'

export function RubricPage() {
  const { rubric: key = '' } = useParams()
  const rubric = findRubric(key)
  if (!rubric) return <NotFoundPage />

  const tiles = rubric.entries.map((entry) => ({
    rubric: key,
    label: rubric.label,
    ...entry,
  }))

  return (
    <>
      <PageHero
        title={<HeroTitle hero={rubric.hero} />}
        back={{ to: '/blog', label: blog.back }}
      >
        {inline(rubric.hero.text)}
      </PageHero>

      <section className="pb-10 sm:pb-16">
        <Container>
          <ArticleTiles tiles={tiles} empty={blog.empty} />
        </Container>
      </section>
    </>
  )
}
