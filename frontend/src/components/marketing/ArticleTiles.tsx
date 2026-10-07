import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { ArticleEntry } from '@/content/articles/types'
import { blog } from '@/content/pages/blog'
import { cn } from '@/lib/cn'

export type ArticleTile = ArticleEntry & { rubric: string; label: string }

type View = 'list' | 'tiles'

const VIEW_KEY = 'blog-view'

const normalize = (s: string) => s.trim().toLowerCase().replaceAll('ё', 'е')

const linkClasses =
  'after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-indigo'

function Arrow({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={cn(
        'text-indigo shrink-0 transition-transform duration-200 group-hover:translate-x-1.5',
        className,
      )}
    >
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  )
}

function Meta({ tile, className }: { tile: ArticleTile; className?: string }) {
  return (
    <p className={cn('text-dim text-[14px] whitespace-nowrap', className)}>
      <time dateTime={tile.published}>
        {tile.published.split('-').reverse().join('.')}
      </time>
      {` · ${tile.readingMinutes} ${blog.minutes}`}
    </p>
  )
}

function ListRow({ tile }: { tile: ArticleTile }) {
  return (
    <li className="group border-line relative grid grid-cols-[minmax(0,1fr)_20px] items-center gap-4 border-t py-4 last:border-b sm:grid-cols-[minmax(0,1fr)_auto_20px] sm:gap-6">
      <div className="min-w-0">
        <h2 className="text-ink group-hover:text-indigo text-[18px] leading-[1.25] font-semibold tracking-[-0.01em] transition-colors sm:text-[20px]">
          <Link
            to={`/blog/${tile.rubric}/${tile.slug}`}
            className={linkClasses}
          >
            {tile.name}
          </Link>
        </h2>
        <p className="text-muted mt-1 text-[14px] max-sm:line-clamp-2 sm:truncate">
          {tile.teaser}
        </p>
        <Meta tile={tile} className="mt-1.5 sm:hidden" />
      </div>
      <Meta tile={tile} className="max-sm:hidden" />
      <Arrow className="size-5" />
    </li>
  )
}

function TileCard({ tile }: { tile: ArticleTile }) {
  return (
    <li className="group border-line bg-card hover:border-line-hover relative flex h-full flex-col rounded-2xl border p-5 transition-colors">
      <Link
        to={`/blog?rubric=${tile.rubric}`}
        className="bg-indigo/12 text-violet-strong focus-visible:outline-indigo relative z-10 self-start rounded-full px-2.5 py-0.5 text-[12px] font-medium focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        {tile.label}
      </Link>
      <h2 className="text-ink group-hover:text-indigo mt-3 text-[20px] leading-[1.25] font-semibold tracking-[-0.01em] transition-colors">
        <Link
          to={`/blog/${tile.rubric}/${tile.slug}`}
          className={cn(linkClasses, 'after:rounded-2xl')}
        >
          {tile.name}
        </Link>
      </h2>
      <p className="text-muted mt-2 mb-4 line-clamp-2 text-[14px]">
        {tile.teaser}
      </p>
      <div className="border-line mt-auto flex items-center justify-between gap-3 border-t pt-3">
        <Meta tile={tile} />
        <Arrow className="size-5" />
      </div>
    </li>
  )
}

function ViewButton({
  label,
  pressed,
  onClick,
  children,
}: {
  label: string
  pressed: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={pressed}
      onClick={onClick}
      className={cn(
        'focus-visible:outline-indigo grid h-9 w-11 place-items-center rounded-[10px] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2',
        pressed ? 'bg-glass-hover text-ink' : 'text-dim hover:text-ink',
      )}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className="size-5"
      >
        {children}
      </svg>
    </button>
  )
}

export function ArticleTiles({
  tiles,
  empty,
  filters,
}: {
  tiles: ArticleTile[]
  empty: string
  filters?: ReactNode
}) {
  const [query, setQuery] = useState('')
  const [view, setView] = useState<View>('list')

  useEffect(() => {
    if (localStorage.getItem(VIEW_KEY) === 'tiles') setView('tiles')
  }, [])

  const choose = (next: View) => {
    setView(next)
    localStorage.setItem(VIEW_KEY, next)
  }

  const needle = normalize(query)
  const found = tiles.filter((t) => normalize(t.name).includes(needle))

  let result: ReactNode
  if (!tiles.length) {
    result = <p className="text-muted text-[16px]">{empty}</p>
  } else if (!found.length) {
    result = (
      <div>
        <p className="text-ink font-display text-[20px] font-semibold">
          {blog.search.emptyTitle}
        </p>
        <p className="text-muted mt-2 text-[16px]">{blog.search.emptyText}</p>
      </div>
    )
  } else {
    result = (
      <>
        <ul className={cn(view === 'tiles' && 'sm:hidden')}>
          {found.map((t) => (
            <ListRow key={`${t.rubric}/${t.slug}`} tile={t} />
          ))}
        </ul>
        {view === 'tiles' && (
          <ul className="hidden gap-4 sm:grid sm:grid-cols-2 lg:grid-cols-3">
            {found.map((t) => (
              <TileCard key={`${t.rubric}/${t.slug}`} tile={t} />
            ))}
          </ul>
        )}
      </>
    )
  }

  return (
    <>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-4">
        {filters}
        <div className="flex w-full items-center gap-3 sm:ml-auto sm:w-auto">
          <label className="relative min-w-0 flex-1 sm:w-[300px] sm:flex-none">
            <span className="sr-only">{blog.search.label}</span>
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="text-dim pointer-events-none absolute top-1/2 left-3.5 size-[18px] -translate-y-1/2"
            >
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-3.5-3.5" />
            </svg>
            <input
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={blog.search.placeholder}
              className="border-line bg-card text-ink placeholder:text-dim focus-visible:border-line-hover focus-visible:outline-indigo h-11 w-full rounded-xl border pr-3 pl-10 text-[16px] transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
            />
          </label>
          <div
            role="group"
            aria-label={blog.view.label}
            className="border-line flex shrink-0 rounded-xl border p-[3px] max-sm:hidden"
          >
            <ViewButton
              label={blog.view.list}
              pressed={view === 'list'}
              onClick={() => choose('list')}
            >
              <path d="M4 6h16M4 12h16M4 18h16" />
            </ViewButton>
            <ViewButton
              label={blog.view.tiles}
              pressed={view === 'tiles'}
              onClick={() => choose('tiles')}
            >
              <rect x="4" y="4" width="6.5" height="6.5" rx="1.5" />
              <rect x="13.5" y="4" width="6.5" height="6.5" rx="1.5" />
              <rect x="4" y="13.5" width="6.5" height="6.5" rx="1.5" />
              <rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.5" />
            </ViewButton>
          </div>
        </div>
      </div>

      <div className="mt-8">{result}</div>
    </>
  )
}
