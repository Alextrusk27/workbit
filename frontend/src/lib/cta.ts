import type { Cta } from '@/content/types'
import { trainingPath } from '@/lib/trainingPath'

export interface CtaLink {
  to: string
  state?: { from: { pathname: string } }
}

export function ctaLink(
  cta: Cta,
  { start, isAuthenticated }: { start: string; isAuthenticated: boolean },
): CtaLink {
  if ('to' in cta) return { to: cta.to }
  const to = 'start' in cta ? start : trainingPath(cta.train)
  return isAuthenticated
    ? { to }
    : { to: '/login', state: { from: { pathname: to } } }
}
