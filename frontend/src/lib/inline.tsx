import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

const TOKEN = /\*\*(.+?)\*\*|\[([^\]]+)\]\(([^)]+)\)|\n/g
const LINK = 'text-indigo hover:text-violet transition-colors'

export function inline(text: string): ReactNode[] {
  const nodes: ReactNode[] = []
  let last = 0
  for (const m of text.matchAll(TOKEN)) {
    if (m.index > last) nodes.push(text.slice(last, m.index))
    const [token, bold, label, href] = m
    if (bold !== undefined) {
      nodes.push(
        <strong key={m.index} className="text-ink font-semibold">
          {bold}
        </strong>,
      )
    } else if (label !== undefined) {
      nodes.push(
        href.startsWith('/') ? (
          <Link key={m.index} to={href} className={LINK}>
            {label}
          </Link>
        ) : (
          <a key={m.index} href={href} className={LINK}>
            {label}
          </a>
        ),
      )
    } else {
      nodes.push(<br key={m.index} />)
    }
    last = m.index + token.length
  }
  if (last < text.length) nodes.push(text.slice(last))
  return nodes
}
