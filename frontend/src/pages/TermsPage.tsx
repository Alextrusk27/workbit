import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

export function TermsPage() {
  usePageTitle('Условия сервиса')
  return (
    <Container className="py-16 sm:py-24">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Правовое
      </p>
      <h1 className="text-ink mt-4 max-w-2xl text-4xl sm:text-5xl">
        Условия сервиса
      </h1>
      <p className="text-muted mt-5 max-w-xl text-lg">
        Текст условий появится здесь позже.
      </p>
    </Container>
  )
}
