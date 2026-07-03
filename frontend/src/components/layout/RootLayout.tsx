import { Outlet } from 'react-router-dom'
import { CookieConsent } from '@/components/CookieConsent'

/** Корневая обёртка над всеми группами роутов: сюда вешаем то, что должно быть
 *  на любой странице (баннер cookie). */
export function RootLayout() {
  return (
    <>
      <Outlet />
      <CookieConsent />
    </>
  )
}
