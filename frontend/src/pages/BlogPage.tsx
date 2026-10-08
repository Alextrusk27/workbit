import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { ArticleTiles } from '@/components/marketing/ArticleTiles'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { rubrics } from '@/content/articles'
import { blog } from '@/content/pages/blog'
import { inline } from '@/lib/inline'

const { hero } = blog

export function BlogPage() {
  const location = useLocation()
  const [rubric, setRubric] = useState<string | null>(null)
  useEffect(() => {
    const value = new URLSearchParams(location.search).get('rubric')
    setRubric(value && Object.hasOwn(rubrics, value) ? value : null)
  }, [location])
  const tiles = Object.entries(rubrics)
    .filter(([key]) => rubric === null || rubric === key)
    .flatMap(([key, r]) =>
      r.entries.map((entry) => ({ rubric: key, label: r.label, ...entry })),
    )
    .sort((a, b) => b.published.localeCompare(a.published))

  return (
    <>
      <PageHero title={<HeroTitle hero={hero} />}>{inline(hero.text)}</PageHero>

      <section className="pb-10 sm:pb-16">
        <Container>
          <ArticleTiles
            tiles={tiles}
            empty={blog.empty}
            filters={
              <div
                className="flex flex-wrap gap-2.5"
                role="group"
                aria-label="Рубрики"
              >
                <Chip
                  selected={rubric === null}
                  onClick={() => setRubric(null)}
                >
                  {blog.all}
                </Chip>
                {Object.entries(rubrics).map(([key, r]) => (
                  <Chip
                    key={key}
                    selected={rubric === key}
                    onClick={() => setRubric(key)}
                  >
                    {r.label}
                  </Chip>
                ))}
              </div>
            }
          />
        </Container>
      </section>
    </>
  )
}
