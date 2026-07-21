import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

const components: Components = {
  h1: ({ children }) => (
    <h1 className="text-ink mt-4 text-4xl sm:text-5xl">{children}</h1>
  ),
  h2: ({ children }) => (
    <h2 className="text-ink border-rule mt-14 border-t pt-8 text-2xl">
      {children}
    </h2>
  ),
  h3: ({ children }) => <h3 className="text-ink mt-8 text-lg">{children}</h3>,
  p: ({ children }) => (
    <p className="text-muted mt-4 leading-relaxed">{children}</p>
  ),
  ul: ({ children }) => (
    <ul className="text-muted mt-4 list-disc space-y-2 pl-5">{children}</ul>
  ),
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  strong: ({ children }) => (
    <strong className="text-ink font-medium">{children}</strong>
  ),
  a: ({ href, children }) => (
    <a
      href={href}
      className="text-accent hover:text-accent-hover underline underline-offset-2 transition-colors"
    >
      {children}
    </a>
  ),
  table: ({ children }) => (
    <div className="mt-6 overflow-x-auto">
      <table className="text-body-sm w-full border-collapse text-left">
        {children}
      </table>
    </div>
  ),
  th: ({ children }) => (
    <th className="text-ink border-rule border-b py-3 pr-6 font-medium">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="text-muted border-rule border-b py-3 pr-6 align-top leading-relaxed">
      {children}
    </td>
  ),
}

type LegalDocumentProps = {
  title: string
  source: string
}

export function LegalDocument({ title, source }: LegalDocumentProps) {
  usePageTitle(title)
  return (
    <Container className="py-16 sm:py-24">
      <article className="animate-rise max-w-3xl">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Правовое
        </p>
        <Markdown remarkPlugins={[remarkGfm]} components={components}>
          {source}
        </Markdown>
      </article>
    </Container>
  )
}
