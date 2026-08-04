import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { CodeForm } from '@/components/auth/CodeForm'
import { authErrorMessage } from '@/features/auth/errors'
import { useRequestCode } from '@/features/auth/useAuth'
import { usePageTitle } from '@/lib/usePageTitle'

export function LoginPage() {
  usePageTitle('Вход')
  const requestCode = useRequestCode()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')

  const from =
    (location.state as { from?: { pathname: string } } | null)?.from
      ?.pathname ?? '/app'

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    requestCode.mutate(email)
  }

  if (requestCode.isSuccess) {
    return (
      <>
        <h1 className="text-ink text-[28px]">Введите код</h1>
        <p className="text-muted mt-3 text-sm leading-relaxed">
          Мы отправили код на <span className="text-ink">{email}</span>. Код
          действует 15 минут.
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
      <h1 className="text-ink text-[28px]">Вход</h1>
      <p className="text-muted mt-2 text-sm">
        Введите почту — пришлём код для входа. Отдельная регистрация не нужна.
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        {requestCode.isError && (
          <Alert>{authErrorMessage(requestCode.error)}</Alert>
        )}
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={requestCode.isPending}
        >
          {requestCode.isPending ? 'Отправляем код…' : 'Получить код'}
        </Button>
        <p className="text-dim text-[12.5px] leading-relaxed">
          Нажимая «Получить код», вы принимаете{' '}
          <Link
            to="/user-agreement"
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted hover:text-ink underline underline-offset-2"
          >
            Пользовательское соглашение
          </Link>{' '}
          и даёте согласие на обработку персональных данных в соответствии с{' '}
          <Link
            to="/privacy"
            target="_blank"
            rel="noopener noreferrer"
            className="text-muted hover:text-ink underline underline-offset-2"
          >
            Политикой конфиденциальности
          </Link>
          .
        </p>
      </form>
    </>
  )
}
