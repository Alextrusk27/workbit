import type { FormEvent } from 'react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { useResetPassword } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function ResetPasswordPage() {
  usePageTitle('Сброс пароля')
  const [params] = useSearchParams()
  const token = params.get('token')
  const reset = useResetPassword()
  const [password, setPassword] = useState('')

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!token) return
    reset.mutate({ token, newPassword: password })
  }

  if (!token) {
    return (
      <>
        <h1 className="text-ink text-[28px]">Ссылка неполная</h1>
        <p className="text-muted mt-3 text-sm">
          В ссылке нет токена сброса. Откройте её из письма целиком или
          запросите сброс заново.
        </p>
        <p className="mt-8 text-sm">
          <Link to="/forgot-password" className="text-indigo hover:text-violet">
            Запросить сброс
          </Link>
        </p>
      </>
    )
  }

  if (reset.isSuccess) {
    return (
      <>
        <h1 className="text-ink text-[28px]">Пароль обновлён</h1>
        <p className="text-muted mt-3 text-sm">
          Теперь войдите с новым паролем.
        </p>
        <p className="mt-8 text-sm">
          <Link to="/login" className="text-indigo hover:text-violet">
            Ко входу
          </Link>
        </p>
      </>
    )
  }

  return (
    <>
      <h1 className="text-ink text-[28px]">Новый пароль</h1>
      <p className="text-muted mt-2 text-sm">
        Придумайте новый пароль для входа.
      </p>

      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        {reset.isError && <Alert>{getErrorMessage(reset.error)}</Alert>}
        <Field
          label="Новый пароль"
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
          disabled={reset.isPending}
        >
          {reset.isPending ? 'Сохраняем…' : 'Сохранить пароль'}
        </Button>
      </form>
    </>
  )
}
