import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { useAuth } from './useAuth'

/** Пускает дальше только залогиненных. «Залогинен ли» определяем по `GET /me`
 *  (куку JS не видит). Пока идёт первая проверка — держим нейтральный экран,
 *  чтобы не мигнуть логином. Сетевая ошибка `/me` — не «разлогинен»: показываем
 *  экран повтора, на форму входа уводит только честный 401. */
export function RequireAuth() {
  const { isAuthenticated, isLoading, isError, refetch } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="text-muted flex min-h-screen items-center justify-center text-sm">
        Загрузка…
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-5 text-center">
        <p className="text-muted text-sm">
          Не удалось связаться с сервером. Проверь соединение и попробуй ещё
          раз.
        </p>
        <Button variant="secondary" onClick={() => void refetch()}>
          Повторить
        </Button>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
