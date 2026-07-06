import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

export function PrivacyPage() {
  usePageTitle('Политика конфиденциальности')
  return (
    <Container className="py-16 sm:py-24">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Правовое
      </p>
      <h1 className="text-ink mt-4 max-w-2xl text-4xl sm:text-5xl">
        Политика конфиденциальности
      </h1>
      <p className="text-muted mt-5 max-w-xl text-lg">
        Текст политики появится здесь позже.
      </p>
    </Container>
  )
}
