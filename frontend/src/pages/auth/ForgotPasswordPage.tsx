import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useForgotPassword } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function ForgotPasswordPage() {
  usePageTitle('Восстановление пароля')
  const forgot = useForgotPassword()
  const [email, setEmail] = useState('')

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    forgot.mutate(email)
  }

  if (forgot.isSuccess) {
    return (
      <>
        <h1 className="text-ink text-3xl">Письмо отправлено</h1>
        <p className="text-muted mt-3 text-sm leading-relaxed">
          Если аккаунт с адресом <span className="text-ink">{email}</span>{' '}
          существует, мы отправили на него ссылку для сброса пароля.
        </p>
        <p className="mt-8 text-sm">
          <Link to="/login" className="text-accent hover:text-accent-hover">
            Вернуться ко входу
          </Link>
        </p>
      </>
    )
  }

  return (
    <>
      <h1 className="text-ink text-3xl">Забыли пароль?</h1>
      <p className="text-muted mt-2 text-sm">
        Укажите email — пришлём ссылку для сброса.
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        {forgot.isError && <Alert>{getErrorMessage(forgot.error)}</Alert>}
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
          disabled={forgot.isPending}
        >
          {forgot.isPending ? 'Отправляем…' : 'Отправить ссылку'}
        </Button>
      </form>

      <p className="mt-6 text-sm">
        <Link
          to="/login"
          className="text-muted hover:text-ink transition-colors"
        >
          Вспомнили? Войти
        </Link>
      </p>
    </>
  )
}
