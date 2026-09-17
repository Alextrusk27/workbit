import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'

/** Шапка внутренней маркетинговой страницы: свечение, заголовок, действия. */
export function PageHero({
  title,
  children,
  actions,
  back,
}: {
  title: ReactNode
  children?: ReactNode
  actions?: ReactNode
  back?: { to: string; label: string }
}) {
  return (
    <header className="glow-page relative overflow-hidden pt-10 pb-12 sm:pt-24 sm:pb-20">
      <Container className="relative text-center">
        {back && (
          <div className="mb-7 text-left">
            <Link
              to={back.to}
              className="text-indigo hover:text-violet inline-block text-sm transition-colors"
            >
              ← {back.label}
            </Link>
          </div>
        )}
        <h1 className="text-ink text-[clamp(32px,4.5vw,48px)] leading-[1.1] font-extrabold tracking-[-0.03em]">
          {title}
        </h1>
        {children && (
          <p className="text-muted mx-auto mt-4 max-w-[56ch] text-[17px]">
            {children}
          </p>
        )}
        {actions && (
          <div className="mt-8 flex flex-wrap justify-center gap-3.5">
            {actions}
          </div>
        )}
      </Container>
    </header>
  )
}
