import { Link } from 'react-router-dom'
import { Logo } from '@/components/ui/Logo'
import type { ArticleEntry } from '@/content/articles/types'

export type ArticleTile = ArticleEntry & { rubric: string; label: string }

export function ArticleTiles({
  tiles,
  empty,
}: {
  tiles: ArticleTile[]
  empty: string
}) {
  if (!tiles.length) {
    return <p className="text-muted text-[16px]">{empty}</p>
  }
  return (
    <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
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
              <div className="text-dim flex items-baseline justify-between gap-3 text-xs">
                <span className="font-semibold tracking-[0.05em] uppercase">
                  {t.label}
                </span>
                <time dateTime={t.published}>
                  {t.published.split('-').reverse().join('.')}
                </time>
              </div>
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
  )
}
