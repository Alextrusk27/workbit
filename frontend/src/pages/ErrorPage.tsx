import { useRouteError } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { usePageTitle } from '@/lib/usePageTitle'

/** errorElement заменяет App целиком, поэтому страница самодостаточна —
 *  без Header/Footer. Ссылка — обычный <a>, чтобы перезагрузкой сбросить
 *  сломанное состояние. */
export function ErrorPage() {
  const error = useRouteError()
  usePageTitle('Ошибка')
  console.error(error)

  return (
    <Container className="py-24 text-center sm:py-32">
      <p className="text-accent font-mono text-sm tracking-[0.2em] uppercase">
        Ошибка
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Что-то пошло не так
      </h1>
      <p className="text-muted mx-auto mt-4 max-w-md">
        Обновите страницу или вернитесь на главную.
      </p>
      <div className="mt-8">
        <a href="/" className={buttonClasses()}>
          На главную
        </a>
      </div>
    </Container>
  )
}
