import { Container } from '@/components/ui/Container'
import { useAuth } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function DashboardPage() {
  usePageTitle('Личный кабинет')
  const { user } = useAuth()

  return (
    <Container className="py-12 sm:py-16">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Личный кабинет
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        С возвращением{user ? `, ${user.email}` : ''}
      </h1>
      <p className="text-muted mt-4 max-w-xl">
        Здесь появятся ваши интервью, разборы и история сессий.
      </p>

      {/* Заглушка списка сессий — наполнится в фазе AI-интервью. */}
      <div className="border-rule mt-10 rounded-lg border border-dashed p-10 text-center">
        <h2 className="text-ink font-display text-xl">Пока нет интервью</h2>
        <p className="text-muted mx-auto mt-2 max-w-md text-sm">
          Модуль прохождения интервью подключим следующим шагом. Скоро отсюда
          можно будет запустить первую сессию.
        </p>
      </div>
    </Container>
  )
}
