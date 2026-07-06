import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useLogin } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function LoginPage() {
  usePageTitle('Вход')
  const login = useLogin()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  const from =
    (location.state as { from?: { pathname: string } } | null)?.from
      ?.pathname ?? '/app'

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await login.mutateAsync({ email, password })
      navigate(from, { replace: true })
    } catch {
      // ошибка показывается через login.isError
    }
  }

  return (
    <>
      <h1 className="text-ink text-3xl">Вход</h1>
      <p className="text-muted mt-2 text-sm">
        Ещё нет аккаунта?{' '}
        <Link to="/register" className="text-accent hover:text-accent-hover">
          Зарегистрироваться
        </Link>
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        {login.isError && <Alert>{getErrorMessage(login.error)}</Alert>}
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
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={login.isPending}
        >
          {login.isPending ? 'Входим…' : 'Войти'}
        </Button>
      </form>

      <p className="mt-6 text-sm">
        <Link
          to="/forgot-password"
          className="text-muted hover:text-ink transition-colors"
        >
          Забыли пароль?
        </Link>
      </p>
    </>
  )
}
