import type { ReactNode } from 'react'
import { Link, useLoaderData } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { PageHero } from '@/components/marketing/PageHero'
import { Reveal } from '@/components/marketing/Reveal'
import type {
  Article,
  ArticleBlock,
  ArticleEntry,
} from '@/content/articles/types'
import { blog } from '@/content/pages/blog'
import type { Cta } from '@/content/types'
import { useAuth } from '@/features/auth/useAuth'
import { ctaLink, type CtaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'
import { NotFoundPage } from '@/pages/NotFoundPage'

type Resolve = (cta: Cta) => CtaLink

function Section({
  id,
  title,
  children,
}: {
  id: string
  title: string
  children: ReactNode
}) {
  return (
    <section id={id} className="scroll-mt-20 pb-10 sm:pb-14">
      <Container>
        <Reveal>
          <h2 className="text-ink text-[clamp(26px,3.2vw,36px)]">{title}</h2>
          <div className="mt-5 flex flex-col gap-4">{children}</div>
        </Reveal>
      </Container>
    </section>
  )
}

function Block({ block, resolve }: { block: ArticleBlock; resolve: Resolve }) {
  if (typeof block === 'string') {
    return (
      <p className="text-muted text-[16px] leading-relaxed">{inline(block)}</p>
    )
  }
  switch (block.type) {
    case 'list':
      return block.ordered ? (
        <ol className="flex flex-col gap-3.5">
          {block.items.map((item, i) => (
            <li key={item} className="flex gap-3.5">
              <span className="border-indigo/25 bg-indigo/12 text-indigo grid size-8 shrink-0 place-items-center rounded-lg border text-sm font-bold">
                {i + 1}
              </span>
              <span className="text-muted pt-1 text-[16px] leading-relaxed">
                {inline(item)}
              </span>
            </li>
          ))}
        </ol>
      ) : (
        <ul className="flex flex-col gap-3.5">
          {block.items.map((item) => (
            <li key={item} className="flex gap-3">
              <span
                aria-hidden
                className="bg-indigo mt-2.5 size-1.5 shrink-0 rounded-full"
              />
              <span className="text-muted text-[16px] leading-relaxed">
                {inline(item)}
              </span>
            </li>
          ))}
        </ul>
      )
    case 'qa':
      return (
        <div className="mt-2 flex flex-col gap-3">
          {block.items.map((item) => (
            <details
              key={item.q}
              className="group border-line bg-card open:border-line-hover rounded-xl border transition-colors"
            >
              <summary className="text-ink flex cursor-pointer list-none items-center justify-between gap-4 px-5.5 py-4.5 text-[17px] font-semibold [&::-webkit-details-marker]:hidden">
                <h3>{item.q}</h3>
                <span
                  aria-hidden
                  className="text-indigo shrink-0 text-[22px] leading-none font-normal transition-transform duration-200 group-open:rotate-45"
                >
                  +
                </span>
              </summary>
              <div className="px-5.5 pb-5">
                <p className="text-muted text-[15px] leading-relaxed">
                  <span className="text-dim font-semibold">
                    Зачем спрашивают.{' '}
                  </span>
                  {inline(item.why)}
                </p>
                <p className="text-muted mt-2 text-[15px] leading-relaxed">
                  <span className="text-dim font-semibold">
                    Что хотят услышать.{' '}
                  </span>
                  {inline(item.what)}
                </p>
              </div>
            </details>
          ))}
        </div>
      )
    case 'cta':
      return (
        <div className="border-indigo/30 bg-indigo/6 mt-2 flex flex-col items-start gap-4 rounded-2xl border px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-muted text-[15px] leading-relaxed">
            {inline(block.text)}
          </p>
          <Link
            {...resolve(block.action)}
            className={buttonClasses({ className: 'px-7' })}
          >
            {block.action.label}
          </Link>
        </div>
      )
  }
}

export function ArticlePage() {
  const data = useLoaderData() as {
    entry: ArticleEntry
    article: Article
  } | null
  const { isAuthenticated } = useAuth()
  if (!data) return <NotFoundPage />

  const { published } = data.entry
  const { hero, intro, sections, sources, cta } = data.article
  const resolve: Resolve = (c) =>
    ctaLink(c, { start: '/app/training/new', isAuthenticated })

  return (
    <>
      <PageHero
        title={<HeroTitle hero={hero} />}
        back={{ to: '/blog', label: blog.back }}
      >
        <span className="text-dim text-[14.5px]">
          {blog.author} ·{' '}
          <time dateTime={published}>
            {new Date(published).toLocaleDateString('ru-RU', {
              day: 'numeric',
              month: 'long',
              year: 'numeric',
              timeZone: 'UTC',
            })}
          </time>
        </span>
        {intro && <span className="mt-2 block">{inline(intro)}</span>}
      </PageHero>

      {sections.map((s) => (
        <Section key={s.id} id={s.id} title={s.title}>
          {s.blocks.map((block, i) => (
            <Block key={i} block={block} resolve={resolve} />
          ))}
        </Section>
      ))}

      {sources && (
        <section className="pb-10 sm:pb-14">
          <Container>
            <aside className="border-line border-t pt-6">
              <h2 className="text-dim text-[13px] font-semibold tracking-[0.08em] uppercase">
                {blog.sources}
              </h2>
              <ul className="mt-3 flex flex-col gap-2">
                {sources.map((item) => (
                  <li
                    key={item}
                    className="text-dim text-[14px] leading-relaxed"
                  >
                    {inline(item)}
                  </li>
                ))}
              </ul>
            </aside>
          </Container>
        </section>
      )}

      <section className="pb-10 sm:pb-16">
        <Container>
          <Reveal>
            <CtaPanel
              title={cta.title}
              wide
              actions={
                <div className="flex flex-col items-center gap-4">
                  <div className="flex flex-wrap justify-center gap-3.5">
                    <Link
                      {...resolve(cta.primary)}
                      className={buttonClasses({ className: 'px-7' })}
                    >
                      {cta.primary.label}
                    </Link>
                    {cta.secondary && (
                      <Link
                        {...resolve(cta.secondary)}
                        className={buttonClasses({
                          variant: 'secondary',
                          className: 'px-7',
                        })}
                      >
                        {cta.secondary.label}
                      </Link>
                    )}
                  </div>
                  {cta.note && (
                    <p className="text-dim text-[13.5px]">{inline(cta.note)}</p>
                  )}
                </div>
              }
            >
              {inline(cta.body)}
            </CtaPanel>
          </Reveal>
        </Container>
      </section>
    </>
  )
}
