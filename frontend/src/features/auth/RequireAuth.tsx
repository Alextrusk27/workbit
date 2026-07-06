import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './useAuth'

/** Пускает дальше только залогиненных. «Залогинен ли» определяем по `GET /me`
 *  (куку JS не видит). Пока идёт первая проверка — держим нейтральный экран,
 *  чтобы не мигнуть логином. */
export function RequireAuth() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()

  if (isLoading) {
    return (
      <div className="text-muted flex min-h-screen items-center justify-center text-sm">
        Загрузка…
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}
