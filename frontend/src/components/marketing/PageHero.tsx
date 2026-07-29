import type { ReactNode } from 'react'
import { Container } from '@/components/ui/Container'
import { Badge } from '@/components/marketing/Badge'

/** Шапка внутренней маркетинговой страницы: свечение, заголовок, действия. */
export function PageHero({
  badge,
  title,
  children,
  actions,
}: {
  badge?: string
  title: ReactNode
  children: ReactNode
  actions?: ReactNode
}) {
  return (
    <header className="glow-page relative overflow-hidden pt-20">
      <Container className="relative text-center">
        {badge && <Badge>{badge}</Badge>}
        <h1 className="text-ink mt-6 text-[clamp(32px,4.5vw,48px)] leading-[1.1] font-extrabold tracking-[-0.03em]">
          {title}
        </h1>
        <p className="text-muted mx-auto mt-4 max-w-[54ch] text-[17px]">
          {children}
        </p>
        {actions && (
          <div className="mt-8 flex flex-wrap justify-center gap-3.5">
            {actions}
          </div>
        )}
      </Container>
    </header>
  )
}
