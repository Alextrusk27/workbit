import type { FormEvent } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Field } from '@/components/ui/Field'
import {
  useAuth,
  useChangePassword,
  useDeleteAccount,
} from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function SettingsPage() {
  usePageTitle('Настройки')
  const { user } = useAuth()

  return (
    <Container className="py-12 sm:py-16">
      <div className="max-w-xl">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Настройки
        </p>
        <h1 className="text-ink mt-4 text-3xl sm:text-4xl">Аккаунт</h1>
        {user && (
          <p className="text-muted mt-2 text-sm">
            Вы вошли как <span className="text-ink">{user.email}</span>
          </p>
        )}

        <div className="mt-12 space-y-14">
          <ChangePasswordSection />
          <DeleteAccountSection />
        </div>
      </div>
    </Container>
  )
}

function ChangePasswordSection() {
  const change = useChangePassword()
  const [oldPassword, setOldPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    change.mutate(
      { oldPassword, newPassword },
      {
        onSuccess: () => {
          setOldPassword('')
          setNewPassword('')
        },
      },
    )
  }

  return (
    <section>
      <h2 className="text-ink font-display text-xl">Смена пароля</h2>
      <p className="text-muted mt-2 text-sm">
        После смены пароля активные сессии на других устройствах завершатся.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-5">
        {change.isError && <Alert>{getErrorMessage(change.error)}</Alert>}
        {change.isSuccess && <Alert tone="success">Пароль обновлён.</Alert>}
        <Field
          label="Текущий пароль"
          type="password"
          autoComplete="current-password"
          required
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
        />
        <Field
          label="Новый пароль"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          hint="Минимум 8 символов"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <Button type="submit" disabled={change.isPending}>
          {change.isPending ? 'Сохраняем…' : 'Сменить пароль'}
        </Button>
      </form>
    </section>
  )
}

function DeleteAccountSection() {
  const navigate = useNavigate()
  const del = useDeleteAccount()

  const onDelete = () => {
    if (
      !window.confirm(
        'Удалить аккаунт? Все интервью и отчёты будут потеряны. Действие необратимо.',
      )
    )
      return
    del.mutate(undefined, {
      onSuccess: () => navigate('/', { replace: true }),
    })
  }

  return (
    <section className="border-rule border-t pt-14">
      <h2 className="text-ink font-display text-xl">Удаление аккаунта</h2>
      <p className="text-muted mt-2 max-w-md text-sm">
        Аккаунт и вся история интервью удаляются безвозвратно. Восстановить их
        будет нельзя.
      </p>

      {del.isError && (
        <div className="mt-5">
          <Alert>{getErrorMessage(del.error)}</Alert>
        </div>
      )}

      <button
        type="button"
        onClick={onDelete}
        disabled={del.isPending}
        className="border-accent/40 text-accent hover:bg-accent/5 mt-6 rounded-md border px-4 py-2 text-sm transition-colors disabled:opacity-50"
      >
        {del.isPending ? 'Удаляем…' : 'Удалить аккаунт'}
      </button>
    </section>
  )
}
