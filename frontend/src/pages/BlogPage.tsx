import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
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

  return (
    <>
      <PageHero title={<HeroTitle hero={hero} />}>{inline(hero.text)}</PageHero>

      <section className="pb-10 sm:pb-16">
        <Container>
          <div
            className="flex flex-wrap gap-2.5"
            role="group"
            aria-label="Рубрики"
          >
            <Chip selected={rubric === null} onClick={() => setRubric(null)}>
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

          {tiles.length ? (
            <ul className="mt-8 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
              {tiles.map((t) => (
                <li key={`${t.rubric}/${t.slug}`}>
                  <Link
                    to={`/blog/${t.rubric}/${t.slug}`}
                    className="border-line bg-card hover:border-line-hover flex h-full flex-col overflow-hidden rounded-2xl border transition hover:-translate-y-[3px]"
                  >
                    <div className="border-line grid aspect-video place-items-center border-b bg-[linear-gradient(135deg,rgba(99,102,241,0.16),rgba(139,92,246,0.12)_60%,rgba(103,232,249,0.06))]">
                      <Logo className="text-[34px]" />
                    </div>
                    <div className="flex flex-1 flex-col gap-2.5 p-6">
                      <span className="text-dim text-xs font-semibold tracking-[0.05em] uppercase">
                        {t.label}
                      </span>
                      <h2 className="text-ink text-[17px] font-semibold tracking-[-0.01em]">
                        {t.name}
                      </h2>
                      <p className="text-muted line-clamp-3 text-[14.5px]">
                        {t.description}
                      </p>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-muted mt-8 text-[16px]">{blog.empty}</p>
          )}
        </Container>
      </section>
    </>
  )
}
