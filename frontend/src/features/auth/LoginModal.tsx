import type { FormEvent, ReactNode } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useBlocker, useLocation, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { CodeForm } from '@/components/auth/CodeForm'
import { IconClose } from '@/components/marketing/icons'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Checkbox } from '@/components/ui/Checkbox'
import { Field } from '@/components/ui/Field'
import { LimitIcon } from '@/components/ui/LimitIcon'
import {
  modalOverlayClasses,
  modalPanelClasses,
} from '@/components/ui/modalStyles'
import { captchaEnabled } from '@/features/auth/captcha'
import { authErrorMessage } from '@/features/auth/errors'
import { WELCOME_LIMITS } from '@/content/limits'
import { useAuth, useRequestCode } from '@/features/auth/useAuth'
import {
  LoginModalContext,
  type LoginModalOptions,
} from '@/features/auth/useLoginModal'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { limitsWord } from '@/lib/plural'
import { useModalA11y } from '@/lib/useModalA11y'

interface LoginModalState {
  open: boolean
  seq: number
  options: LoginModalOptions
}

export function LoginModalProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<LoginModalState>({
    open: false,
    seq: 0,
    options: {},
  })
  const open = useCallback(
    (options: LoginModalOptions = {}) =>
      setState((s) => ({ open: true, seq: s.seq + 1, options })),
    [],
  )
  const close = useCallback(() => setState((s) => ({ ...s, open: false })), [])

  const { pathname } = useLocation()
  const previousPathname = useRef(pathname)
  useEffect(() => {
    const from = previousPathname.current
    previousPathname.current = pathname
    if (from !== '/login' && pathname !== '/login') close()
  }, [pathname, close])

  const { isAuthenticated, isLoading } = useAuth()
  const blocker = useBlocker(
    useCallback(
      ({ nextLocation, historyAction }) =>
        historyAction === 'PUSH' &&
        nextLocation.pathname === '/login' &&
        !isLoading &&
        !isAuthenticated,
      [isLoading, isAuthenticated],
    ),
  )
  useEffect(() => {
    if (blocker.state !== 'blocked') return
    const from = (
      blocker.location.state as { from?: { pathname: string } } | null
    )?.from?.pathname
    open({ from })
    blocker.reset()
  }, [blocker, open])

  return (
    <LoginModalContext.Provider value={open}>
      {children}
      <LoginModal
        key={state.seq}
        open={state.open}
        options={state.options}
        onClose={close}
      />
    </LoginModalContext.Provider>
  )
}

function LoginModal({
  open,
  options,
  onClose,
}: {
  open: boolean
  options: LoginModalOptions
  onClose: () => void
}) {
  const panelRef = useModalA11y(open)

  useEffect(() => {
    if (!open) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [open, onClose])

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: motionTokens.duration.fast }}
          className={cn(modalOverlayClasses, 'overflow-y-auto')}
          onClick={(e) => {
            if (e.target === e.currentTarget) onClose()
          }}
        >
          <motion.div
            initial={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: motionTokens.distance.sm, scale: 0.98 }}
            transition={{
              duration: motionTokens.duration.fast,
              ease: motionTokens.easing.smooth,
            }}
            ref={panelRef}
            role="dialog"
            aria-modal="true"
            aria-label="Вход"
            className="mx-auto my-auto flex min-h-full w-full max-w-[440px] flex-col justify-center"
            onClick={(e) => {
              if (e.target === e.currentTarget) onClose()
            }}
          >
            <div className={modalPanelClasses}>
              <LoginForm options={options} onClose={onClose} />
              <button
                type="button"
                onClick={onClose}
                aria-label="Закрыть"
                className="text-muted hover:text-ink hover:bg-indigo/8 absolute top-4 right-4 inline-flex size-9 items-center justify-center rounded-md transition-colors"
              >
                <IconClose className="size-5" />
              </button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  )
}

function LoginForm({
  options,
  onClose,
}: {
  options: LoginModalOptions
  onClose: () => void
}) {
  const requestCode = useRequestCode()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [consent, setConsent] = useState(false)

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    requestCode.mutate({ email, personalDataConsent: consent })
  }

  const onLoggedIn = () => {
    onClose()
    if (options.onSuccess) options.onSuccess()
    else navigate(options.from ?? '/app')
  }

  if (requestCode.isSuccess) {
    return (
      <>
        <h2 className="text-ink pr-9 text-[22px] font-bold">Введи код</h2>
        <p className="text-muted mt-3 text-[15px] leading-relaxed">
          Мы отправили код на <span className="text-ink">{email}</span>.
        </p>
        <CodeForm email={email} onSuccess={onLoggedIn} />
      </>
    )
  }

  return (
    <>
      <h2 className="text-ink pr-9 text-[22px] font-bold">Войти</h2>
      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        {requestCode.isError && (
          <Alert>{authErrorMessage(requestCode.error)}</Alert>
        )}
        <Field
          className="[&>input]:text-base [&>label]:text-sm"
          label="Email"
          placeholder="you@example.com"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Checkbox checked={consent} onChange={setConsent} required>
          Соглашаюсь на обработку персональных данных в соответствии с{' '}
          <Link
            to="/privacy"
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted hover:text-ink underline underline-offset-2"
          >
            Политикой конфиденциальности
          </Link>
        </Checkbox>
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={requestCode.isPending}
        >
          {requestCode.isPending ? 'Отправляем код…' : 'Получить код'}
        </Button>
        <p className="text-dim text-[13px] leading-relaxed">
          Нажимая «Получить код», ты принимаешь{' '}
          <Link
            to="/user-agreement"
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted hover:text-ink whitespace-nowrap underline underline-offset-2"
          >
            Пользовательское соглашение
          </Link>
          .
        </p>
        {captchaEnabled && (
          <p className="text-dim text-[13px] leading-relaxed">
            Сайт защищён Yandex SmartCaptcha в соответствии с{' '}
            <a
              href="https://yandex.ru/legal/smartcaptcha_notice/ru/"
              target="_blank"
              rel="noopener noreferrer"
              className="text-muted hover:text-ink whitespace-nowrap underline underline-offset-2"
            >
              Политикой обработки данных
            </a>
            .
          </p>
        )}
      </form>

      <p className="bg-violet/13 border-violet/22 text-violet-strong mt-6 flex items-center gap-2.5 rounded-lg border px-4 py-3 text-[14px] font-semibold">
        <LimitIcon className="size-4 shrink-0" />
        {WELCOME_LIMITS} {limitsWord(WELCOME_LIMITS)} в подарок при первом входе
      </p>
    </>
  )
}
