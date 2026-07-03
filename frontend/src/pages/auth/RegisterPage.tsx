import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useRegister } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function RegisterPage() {
  usePageTitle('Регистрация')
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    register.mutate({ email, password })
  }

  if (register.isSuccess) {
    return (
      <>
        <h1 className="text-ink text-3xl">Проверьте почту</h1>
        <p className="text-muted mt-3 text-sm leading-relaxed">
          Мы отправили письмо со ссылкой подтверждения на{' '}
          <span className="text-ink">{email}</span>. Перейдите по ней, чтобы
          завершить регистрацию.
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
      <h1 className="text-ink text-3xl">Регистрация</h1>
      <p className="text-muted mt-2 text-sm">
        Уже есть аккаунт?{' '}
        <Link to="/login" className="text-accent hover:text-accent-hover">
          Войти
        </Link>
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        {register.isError && <Alert>{getErrorMessage(register.error)}</Alert>}
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          label="Пароль"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          hint="Минимум 8 символов"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={register.isPending}
        >
          {register.isPending ? 'Создаём…' : 'Создать аккаунт'}
        </Button>
      </form>
    </>
  )
}
