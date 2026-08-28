import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Checkbox } from '@/components/ui/Checkbox'
import { Field } from '@/components/ui/Field'
import { CodeForm } from '@/components/auth/CodeForm'
import { captchaEnabled } from '@/features/auth/captcha'
import { authErrorMessage } from '@/features/auth/errors'
import { useRequestCode } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function LoginPage() {
  usePageTitle('Вход')
  const requestCode = useRequestCode()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [consent, setConsent] = useState(false)

  const from =
    (location.state as { from?: { pathname: string } } | null)?.from
      ?.pathname ?? '/app'

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    requestCode.mutate({ email, personalDataConsent: consent })
  }

  if (requestCode.isSuccess) {
    return (
      <>
        <h1 className="text-ink text-[26px] lg:text-[32px]">Введи код</h1>
        <p className="text-muted mt-3 text-[15px] leading-relaxed">
          Мы отправили код на <span className="text-ink">{email}</span>.
        </p>
        <CodeForm
          email={email}
          onSuccess={() => navigate(from, { replace: true })}
        />
      </>
    )
  }

  return (
    <>
      <h1 className="text-ink text-[26px] lg:text-[32px]">Войти</h1>
      <p className="text-muted mt-3 text-[15px] leading-relaxed lg:text-[15.5px]">
        Введи почту — пришлём код для входа.
      </p>
      <form
        onSubmit={onSubmit}
        className="mt-7 space-y-5 lg:mt-8.5 lg:space-y-5.5"
      >
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

      <div className="border-divider mt-6.5 border-t pt-4.5 lg:hidden">
        <p className="text-dim text-[13px]">
          Впервые здесь? Аккаунт создастся автоматически.
        </p>
      </div>
    </>
  )
}
