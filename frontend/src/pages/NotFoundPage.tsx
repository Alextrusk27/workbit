import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'

export function NotFoundPage() {
  return (
    <Container className="py-24 text-center sm:py-32">
      <Eyebrow className="text-indigo">404</Eyebrow>
      <h1 className="text-ink mt-4 text-[clamp(28px,3.6vw,40px)]">
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
