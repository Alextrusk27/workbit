import { useEffect, useRef } from 'react'
import {
  Navigate,
  useLocation,
  useNavigate,
  useNavigationType,
} from 'react-router-dom'
import { useAuth } from './useAuth'
import { useLoginModal } from './useLoginModal'

/** Адрес `/login` без своей страницы: открывает модалку входа и уводит с адреса —
 *  назад на страницу, откуда пришли по ссылке внутри приложения, иначе на главную
 *  (прямой заход, ссылка из письма, редирект `RequireAuth`). Цель после входа
 *  берётся из `state.from`. Залогиненного сразу отправляем в ЛК. */
export function LoginRoute() {
  const { isAuthenticated, isLoading } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const navigationType = useNavigationType()
  const openLogin = useLoginModal()
  const handled = useRef(false)

  useEffect(() => {
    if (isLoading || isAuthenticated || handled.current) return
    handled.current = true
    const from = (location.state as { from?: { pathname: string } } | null)
      ?.from?.pathname
    openLogin({ from })
    if (navigationType === 'PUSH') navigate(-1)
    else navigate('/', { replace: true })
  }, [
    isLoading,
    isAuthenticated,
    location.state,
    navigationType,
    navigate,
    openLogin,
  ])

  if (!isLoading && isAuthenticated) {
    return <Navigate to="/app" replace />
  }

  return null
}
