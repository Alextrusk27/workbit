import { useRouteError } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
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
      <Eyebrow className="text-indigo">Ошибка</Eyebrow>
      <h1 className="text-ink mt-4 text-[clamp(28px,3.6vw,40px)]">
        Что-то пошло не так
      </h1>
      <p className="text-muted mx-auto mt-4 max-w-md">
        Обнови страницу или вернись на главную.
      </p>
      <div className="mt-8">
        <a href="/" className={buttonClasses()}>
          На главную
        </a>
      </div>
    </Container>
  )
}
