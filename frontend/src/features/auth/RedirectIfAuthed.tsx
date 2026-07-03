import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from './useAuth'

/** Обратный `RequireAuth`: залогиненного не пускаем на страницы входа/регистрации,
 *  а уводим в ЛК. Так любая ссылка на `/login` для авторизованного пользователя
 *  не показывает форму входа. Пока идёт первая проверка — показываем форму
 *  (в приложении сюда попадают по ссылке, где `/me` уже в кэше — мигания нет). */
export function RedirectIfAuthed() {
  const { isAuthenticated, isLoading } = useAuth()

  if (!isLoading && isAuthenticated) {
    return <Navigate to="/app" replace />
  }

  return <Outlet />
}
