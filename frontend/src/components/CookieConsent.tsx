import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'

const CONSENT_KEY = 'workbit-cookie-consent'

/** Баннер согласия на cookie. Показывается, пока пользователь не принял; выбор
 *  запоминаем в localStorage (ленивое чтение в initial state — без мигания). */
export function CookieConsent() {
  const [visible, setVisible] = useState(
    () => !localStorage.getItem(CONSENT_KEY),
  )

  if (!visible) return null

  const accept = () => {
    localStorage.setItem(CONSENT_KEY, 'accepted')
    setVisible(false)
  }

  return (
    <div
      role="region"
      aria-label="Согласие на использование cookie"
      className="border-rule bg-paper/95 fixed inset-x-0 bottom-0 z-50 border-t pb-[env(safe-area-inset-bottom)] backdrop-blur"
    >
      <Container className="flex flex-col gap-4 py-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-ink/85 text-sm">
          Мы используем cookie, чтобы сайт работал и вход сохранялся. Продолжая,
          вы соглашаетесь с этим — подробности в{' '}
          <Link
            to="/privacy"
            className="text-accent hover:text-accent-hover underline underline-offset-2"
          >
            политике конфиденциальности
          </Link>
          .
        </p>
        <Button onClick={accept} className="shrink-0 max-sm:w-full">
          Принять
        </Button>
      </Container>
    </div>
  )
}
