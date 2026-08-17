import { useEffect, useRef } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { CookieConsent } from '@/components/CookieConsent'

declare global {
  interface Window {
    ym?: (id: number, action: string, ...params: unknown[]) => void
  }
}

const METRIKA_ID = 111653697

/** Корневая обёртка над всеми группами роутов: сюда вешаем то, что должно быть
 *  на любой странице (баннер cookie, хиты Метрики). */
export function RootLayout() {
  const location = useLocation()
  const firstHit = useRef(true)

  useEffect(() => {
    if (firstHit.current) {
      firstHit.current = false
      return
    }
    window.ym?.(METRIKA_ID, 'hit', location.pathname + location.search)
  }, [location])

  return (
    <>
      <Outlet />
      <CookieConsent />
    </>
  )
}
