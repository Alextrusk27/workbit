import { useEffect, useRef } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { AccountDeletedModal } from '@/components/AccountDeletedModal'
import { CookieConsent } from '@/components/CookieConsent'
import { LoginModalProvider } from '@/features/auth/LoginModal'
import { TopUpModalProvider } from '@/features/billing/TopUpModal'
import { WelcomeProvider } from '@/features/auth/WelcomeModal'
import { METRIKA_ID } from '@/lib/metrika'

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
    <WelcomeProvider>
      <LoginModalProvider>
        <TopUpModalProvider>
          <Outlet />
          <CookieConsent />
          <AccountDeletedModal />
        </TopUpModalProvider>
      </LoginModalProvider>
    </WelcomeProvider>
  )
}
