import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

export function NotFoundPage() {
  usePageTitle('Страница не найдена')
  return (
    <Container className="py-24 text-center sm:py-32">
      <p className="text-accent font-mono text-sm tracking-[0.2em] uppercase">
        404
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Страница не найдена
      </h1>
      <p className="text-muted mx-auto mt-4 max-w-md">
        Такой страницы нет или она ещё не готова. Вернитесь на главную и начните
        отсюда.
      </p>
      <div className="mt-8">
        <Link to="/" className={buttonClasses()}>
          На главную
        </Link>
      </div>
    </Container>
  )
}
