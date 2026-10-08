import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLoaderData, useParams } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { CtaPanel } from '@/components/marketing/CtaPanel'
import { HeroTitle } from '@/components/marketing/HeroTitle'
import { Reveal } from '@/components/marketing/Reveal'
import { findRubric } from '@/content/articles'
import type {
  Article,
  ArticleBlock,
  ArticleEntry,
  ArticleSection,
  QaItem,
} from '@/content/articles/types'
import { blog } from '@/content/pages/blog'
import type { Cta } from '@/content/types'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'
import { ctaLink, type CtaLink } from '@/lib/cta'
import { inline } from '@/lib/inline'
import { questionsWord } from '@/lib/plural'
import { NotFoundPage } from '@/pages/NotFoundPage'

type Resolve = (cta: Cta) => CtaLink

const text = blog.article
const GRID =
  'grid grid-cols-[minmax(0,1fr)] gap-y-10 lg:grid-cols-[220px_minmax(0,720px)] lg:justify-center lg:gap-x-16'
const LABEL = 'text-[12px] font-semibold tracking-[0.08em] uppercase'
const BODY = 'text-muted text-[17px] leading-[1.7]'

function questionStarts(sections: ArticleSection[]): Map<ArticleBlock, number> {
  const starts = new Map<ArticleBlock, number>()
  let next = 1
  for (const block of sections.flatMap((s) => s.blocks)) {
    if (typeof block === 'object' && block.type === 'qa') {
      starts.set(block, next)
      next += block.items.length
    }
  }
  return starts
}

function useActiveSection(ids: string[]): string | null {
  const [active, setActive] = useState<string | null>(null)
  const key = ids.join(' ')

  useEffect(() => {
    const list = key.split(' ')
    let frame = 0
    const update = () => {
      frame = 0
      let current: string | null = null
      for (const id of list) {
        const el = document.getElementById(id)
        if (el && el.getBoundingClientRect().top <= 120) current = id
      }
      setActive(current)
    }
    const schedule = () => {
      if (!frame) frame = requestAnimationFrame(update)
    }
    schedule()
    window.addEventListener('scroll', schedule, { passive: true })
    return () => {
      window.removeEventListener('scroll', schedule)
      cancelAnimationFrame(frame)
    }
  }, [key])

  return active
}

function Toc({ sections }: { sections: ArticleSection[] }) {
  const active = useActiveSection(sections.map((s) => s.id))
  return (
    <nav
      aria-label={text.toc}
      className="border-line bg-card rounded-xl border p-5 lg:border-0 lg:bg-transparent lg:p-0"
    >
      <p className={`text-dim ${LABEL} lg:hidden`}>{text.toc}</p>
      <ol className="lg:border-line mt-3 flex flex-col gap-1 lg:mt-0 lg:border-l">
        {sections.map((s) => (
          <li key={s.id}>
            <Link
              to={`#${s.id}`}
              aria-current={active === s.id ? 'location' : undefined}
              className={`block py-1 text-[14.5px] leading-snug transition-colors lg:-ml-px lg:border-l-2 lg:py-1.5 lg:pl-4 ${
                active === s.id
                  ? 'text-ink lg:border-indigo'
                  : 'text-muted hover:text-ink border-transparent'
              }`}
            >
              {s.title}
            </Link>
          </li>
        ))}
      </ol>
    </nav>
  )
}

function SideCta({ cta, resolve }: { cta: Article['cta']; resolve: Resolve }) {
  return (
    <div className="border-line bg-card mt-8 rounded-xl border p-5 max-lg:hidden">
      <Link
        {...resolve(cta.primary)}
        className={buttonClasses({ size: 'sm', className: 'w-full' })}
      >
        {cta.primary.label}
      </Link>
      {cta.secondary && (
        <Link
          {...resolve(cta.secondary)}
          className="text-indigo hover:text-violet mt-3 block text-center text-[14px] transition-colors"
        >
          {cta.secondary.label}
        </Link>
      )}
    </div>
  )
}

function QaCard({
  item,
  n,
  onToggle,
}: {
  item: QaItem
  n: number
  onToggle: () => void
}) {
  return (
    <details
      onToggle={onToggle}
      className="group border-line bg-card open:border-line-hover rounded-xl border transition-colors"
    >
      <summary className="flex cursor-pointer list-none items-start gap-4 px-5 py-4 sm:px-6 [&::-webkit-details-marker]:hidden">
        <span
          aria-hidden
          className="text-dim font-display w-6 shrink-0 pt-[3px] text-[13px] tabular-nums"
        >
          {String(n).padStart(2, '0')}
        </span>
        <h3 className="text-ink min-w-0 flex-1 text-[17px] leading-snug font-semibold">
          {item.q}
        </h3>
        <span
          aria-hidden
          className="text-indigo shrink-0 text-[22px] leading-none font-normal transition-transform duration-200 group-open:rotate-45"
        >
          +
        </span>
      </summary>
      <div className="flex flex-col gap-4 px-5 pb-5 sm:pr-6 sm:pl-15">
        <div>
          <p className={`text-dim ${LABEL}`}>{text.why}</p>
          <p className="text-muted mt-1.5 text-[15.5px] leading-relaxed">
            {inline(item.why)}
          </p>
        </div>
        <div className="bg-surface border-surface-line rounded-lg border px-4 py-3.5">
          <p className={`text-indigo ${LABEL}`}>{text.what}</p>
          <p className="text-ink mt-1.5 text-[15.5px] leading-relaxed">
            {inline(item.what)}
          </p>
        </div>
        {item.mistake && (
          <div>
            <p className={`text-danger ${LABEL}`}>{text.mistake}</p>
            <p className="text-muted mt-1.5 text-[15.5px] leading-relaxed">
              {inline(item.mistake)}
            </p>
          </div>
        )}
      </div>
    </details>
  )
}

function QaList({ items, start }: { items: QaItem[]; start: number }) {
  const ref = useRef<HTMLDivElement>(null)
  const [allOpen, setAllOpen] = useState(false)
  const all = () => [...(ref.current?.querySelectorAll('details') ?? [])]
  const sync = () => setAllOpen(all().every((d) => d.open))
  const toggleAll = () => {
    const next = !allOpen
    for (const d of all()) d.open = next
    setAllOpen(next)
  }

  return (
    <div className="mt-1">
      {items.length > 2 && (
        <div className="mb-3 flex justify-end">
          <button
            type="button"
            onClick={toggleAll}
            className="text-indigo hover:text-violet text-[14px] transition-colors"
          >
            {allOpen ? text.collapse : text.expand}
          </button>
        </div>
      )}
      <div ref={ref} className="flex flex-col gap-3">
        {items.map((item, i) => (
          <QaCard key={item.q} item={item} n={start + i} onToggle={sync} />
        ))}
      </div>
    </div>
  )
}

function Table({ block }: { block: Extract<ArticleBlock, { type: 'table' }> }) {
  const stack = block.head.length > 2
  const cell = stack ? 'max-sm:block max-sm:py-1' : ''
  return (
    <figure className="mt-1">
      <div className="border-line overflow-x-auto rounded-xl border">
        <table
          className={cn(
            'w-full border-collapse text-left text-[15px] leading-snug',
            stack && 'max-sm:block',
          )}
        >
          <thead className={cn('bg-surface', stack && 'max-sm:hidden')}>
            <tr>
              {block.head.map((h) => (
                <th
                  key={h}
                  scope="col"
                  className={`text-dim px-4 py-3 align-bottom ${LABEL}`}
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className={cn(stack && 'max-sm:block')}>
            {block.rows.map((row) => (
              <tr
                key={row[0]}
                className={cn(
                  'border-line border-t',
                  stack && 'max-sm:block max-sm:py-2 max-sm:first:border-t-0',
                )}
              >
                {row.map((text, i) =>
                  i === 0 ? (
                    <th
                      key={i}
                      scope="row"
                      className={cn(
                        'text-ink px-4 py-3 align-top font-medium',
                        cell,
                      )}
                    >
                      {inline(text)}
                    </th>
                  ) : (
                    <td
                      key={i}
                      className={cn('text-muted px-4 py-3 align-top', cell)}
                    >
                      {stack && (
                        <span
                          className={`text-dim mb-0.5 block sm:hidden ${LABEL}`}
                        >
                          {block.head[i]}
                        </span>
                      )}
                      {inline(text)}
                    </td>
                  ),
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {block.caption && (
        <figcaption className="text-dim mt-2.5 text-[13.5px] leading-relaxed">
          {inline(block.caption)}
        </figcaption>
      )}
    </figure>
  )
}

function Block({
  block,
  resolve,
  start,
}: {
  block: ArticleBlock
  resolve: Resolve
  start: number
}) {
  if (typeof block === 'string') {
    return <p className={BODY}>{inline(block)}</p>
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
              <span className={`${BODY} pt-0.5`}>{inline(item)}</span>
            </li>
          ))}
        </ol>
      ) : (
        <ul className="flex flex-col gap-3">
          {block.items.map((item) => (
            <li key={item} className="flex gap-3">
              <span
                aria-hidden
                className="bg-indigo mt-[0.72em] size-1.5 shrink-0 rounded-full"
              />
              <span className={BODY}>{inline(item)}</span>
            </li>
          ))}
        </ul>
      )
    case 'qa':
      return <QaList items={block.items} start={start} />
    case 'table':
      return <Table block={block} />
    case 'tip':
      return (
        <aside className="bg-surface rounded-xl px-5 py-4">
          <p className="text-muted text-[15.5px] leading-relaxed">
            <span className="text-indigo font-semibold">{text.tip}. </span>
            {inline(block.text)}
          </p>
        </aside>
      )
    case 'cta':
      return (
        <div className="border-indigo/30 bg-indigo/6 mt-1 flex flex-col items-start gap-4 rounded-2xl border px-6 py-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-muted text-[15px] leading-relaxed">
            {inline(block.text)}
          </p>
          <Link
            {...resolve(block.action)}
            className={buttonClasses({ className: 'shrink-0 px-7' })}
          >
            {block.action.label}
          </Link>
        </div>
      )
  }
}

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
    <section id={id} className="scroll-mt-24 pt-12 first:pt-0 sm:pt-16">
      <h2 className="text-ink text-[clamp(24px,2.6vw,30px)] leading-tight text-balance">
        {title}
      </h2>
      <div className="mt-5 flex flex-col gap-4">{children}</div>
    </section>
  )
}

export function ArticlePage() {
  const data = useLoaderData() as {
    entry: ArticleEntry
    article: Article
  } | null
  const { rubric = '' } = useParams()
  const { isAuthenticated } = useAuth()
  if (!data) return <NotFoundPage />

  const { published, updated, readingMinutes } = data.entry
  const date = updated ?? published
  const { hero, intro, sections, sources, cta } = data.article
  const resolve: Resolve = (c) =>
    ctaLink(c, { start: '/app/training/new', isAuthenticated })
  const starts = questionStarts(sections)
  const questions = sections
    .flatMap((s) => s.blocks)
    .reduce(
      (sum, b) =>
        typeof b === 'object' && b.type === 'qa' ? sum + b.items.length : sum,
      0,
    )
  const rubricLabel = findRubric(rubric)?.label

  return (
    <>
      <div
        aria-hidden
        className="read-progress bg-indigo fixed inset-x-0 top-0 z-60 h-0.5"
      />

      <header className="glow-page relative overflow-hidden pt-8 pb-10 sm:pt-16 sm:pb-14">
        <Container className="relative">
          <div className={GRID}>
            <div className="lg:col-start-2">
              <nav
                aria-label="Навигация"
                className="text-dim flex flex-wrap items-center gap-x-2 text-sm"
              >
                <Link
                  to="/blog"
                  className="text-indigo hover:text-violet transition-colors"
                >
                  {blog.back}
                </Link>
                {rubricLabel && (
                  <>
                    <span aria-hidden>/</span>
                    <Link
                      to={`/blog?rubric=${rubric}`}
                      className="text-indigo hover:text-violet transition-colors"
                    >
                      {rubricLabel}
                    </Link>
                  </>
                )}
              </nav>
              <h1 className="text-ink mt-6 text-[clamp(32px,4.5vw,48px)] leading-[1.1] font-semibold tracking-[-0.03em] text-balance">
                <HeroTitle hero={hero} />
              </h1>
              <p className="text-dim mt-5 text-[14.5px]">
                {blog.author} · {updated && `${text.updated} `}
                <time dateTime={date}>
                  {new Date(date).toLocaleDateString('ru-RU', {
                    day: 'numeric',
                    month: 'long',
                    year: 'numeric',
                    timeZone: 'UTC',
                  })}
                </time>{' '}
                · {readingMinutes} {text.readTime}
                {questions > 0 && ` · ${questions} ${questionsWord(questions)}`}
              </p>
              {intro && <p className={`${BODY} mt-5`}>{inline(intro)}</p>}
            </div>
          </div>
        </Container>
      </header>

      <Container className="pb-10 sm:pb-16">
        <div className={GRID}>
          <aside className="lg:sticky lg:top-24 lg:max-h-[calc(100vh-7rem)] lg:self-start lg:overflow-y-auto">
            <Toc sections={sections} />
            <SideCta cta={cta} resolve={resolve} />
          </aside>

          <article className="min-w-0">
            {sections.map((s) => (
              <Section key={s.id} id={s.id} title={s.title}>
                {s.blocks.map((block, i) => (
                  <Block
                    key={i}
                    block={block}
                    resolve={resolve}
                    start={starts.get(block) ?? 1}
                  />
                ))}
              </Section>
            ))}

            {sources && (
              <aside className="border-line mt-14 border-t pt-6">
                <h2 className={`text-dim ${LABEL} text-[13px]`}>
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
            )}

            <Reveal className="mt-12">
              <CtaPanel
                title={cta.title}
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
                      <p className="text-dim text-[13.5px]">
                        {inline(cta.note)}
                      </p>
                    )}
                  </div>
                }
              >
                {inline(cta.body)}
              </CtaPanel>
            </Reveal>
          </article>
        </div>
      </Container>
    </>
  )
}
