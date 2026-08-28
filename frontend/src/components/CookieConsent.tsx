import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { motionTokens, springs } from '@/lib/motion'

const CONSENT_KEY = 'workbit-cookie-consent'

/** Баннер согласия на cookie. Показывается, пока пользователь не принял; выбор
 *  запоминаем в localStorage (чтение после маунта — SSR-совместимо). */
export function CookieConsent() {
  const [visible, setVisible] = useState(false)
  const reduce = useReducedMotion()

  useEffect(() => {
    if (!localStorage.getItem(CONSENT_KEY)) setVisible(true)
  }, [])

  const accept = () => {
    localStorage.setItem(CONSENT_KEY, 'accepted')
    setVisible(false)
  }

  return (
    <AnimatePresence>
      {visible && (
        <motion.div
          key="cookie-consent"
          role="region"
          aria-label="Согласие на использование cookie"
          className="border-line bg-canvas-2 shadow-pop fixed bottom-5 left-1/2 z-60 mb-[env(safe-area-inset-bottom)] flex w-[calc(100%-40px)] max-w-180 -translate-x-1/2 flex-wrap items-center gap-5 rounded-xl border px-5 py-4"
          initial={
            reduce
              ? { opacity: 0 }
              : { opacity: 0, y: motionTokens.distance.md }
          }
          animate={{ opacity: 1, y: 0 }}
          exit={
            reduce
              ? { opacity: 0 }
              : { opacity: 0, y: motionTokens.distance.md }
          }
          transition={springs.gentle}
        >
          <p className="text-muted min-w-70 flex-1 text-[13.5px] leading-relaxed">
            Мы используем cookie, чтобы сайт работал и вход сохранялся.
            Продолжая, ты соглашаешься с этим — подробности в{' '}
            <Link
              to="/privacy"
              className="text-indigo hover:text-violet underline underline-offset-2"
            >
              политике конфиденциальности
            </Link>
            .
          </p>
          <Button onClick={accept} size="sm" className="shrink-0 max-sm:w-full">
            Принять
          </Button>
        </motion.div>
      )}
    </AnimatePresence>
  )
}
