import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Eyebrow } from '@/components/ui/Eyebrow'

interface AppPageHeaderProps {
  back?: { to: string; label: string }
  eyebrow: string
  title: ReactNode
  /** Подводка под заголовком. */
  children?: ReactNode
  actions?: ReactNode
}

/** Шапка страницы кабинета: возврат, надзаголовок, заголовок и действия. */
export function AppPageHeader({
  back,
  eyebrow,
  title,
  children,
  actions,
}: AppPageHeaderProps) {
  return (
    <div>
      {back && (
        <Link
          to={back.to}
          className="text-indigo hover:text-violet mb-7 inline-block text-sm transition-colors"
        >
          ← {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <Eyebrow>{eyebrow}</Eyebrow>
          <h1 className="text-ink mt-2.5 text-[clamp(28px,3.6vw,38px)] font-extrabold">
            {title}
          </h1>
        </div>
        {actions}
      </div>
      {children && <p className="text-muted mt-3.5 max-w-[60ch]">{children}</p>}
    </div>
  )
}
