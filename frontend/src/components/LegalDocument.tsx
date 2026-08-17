import Markdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'

const components: Components = {
  h1: ({ children }) => (
    <h1 className="text-ink mt-4 text-[clamp(32px,4.5vw,44px)] font-extrabold tracking-[-0.03em]">
      {children}
    </h1>
  ),
  h2: ({ children }) => (
    <h2 className="text-ink border-divider mt-14 border-t pt-8 text-2xl">
      {children}
    </h2>
  ),
  h3: ({ children }) => <h3 className="text-ink mt-8 text-lg">{children}</h3>,
  p: ({ children }) => <p className="text-muted mt-4">{children}</p>,
  ul: ({ children }) => (
    <ul className="text-muted mt-4 list-disc space-y-2 pl-5">{children}</ul>
  ),
  li: ({ children }) => <li>{children}</li>,
  strong: ({ children }) => (
    <strong className="text-ink font-semibold">{children}</strong>
  ),
  a: ({ href, children }) => (
    <a
      href={href}
      className="text-indigo hover:text-violet underline underline-offset-2 transition-colors"
    >
      {children}
    </a>
  ),
  table: ({ children }) => (
    <div className="mt-6 overflow-x-auto">
      <table className="w-full border-collapse text-left text-[14.5px]">
        {children}
      </table>
    </div>
  ),
  th: ({ children }) => (
    <th className="text-ink border-line border-b py-3 pr-6 font-semibold">
      {children}
    </th>
  ),
  td: ({ children }) => (
    <td className="text-muted border-line border-b py-3 pr-6 align-top">
      {children}
    </td>
  ),
}

type LegalDocumentProps = {
  source: string
}

export function LegalDocument({ source }: LegalDocumentProps) {
  return (
    <Container className="py-16">
      <article className="max-w-3xl">
        <Eyebrow>Правовое</Eyebrow>
        <Markdown remarkPlugins={[remarkGfm]} components={components}>
          {source}
        </Markdown>
      </article>
    </Container>
  )
}
