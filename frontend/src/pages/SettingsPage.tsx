import type { FormEvent } from 'react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
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
    <Container className="max-w-160">
      <AppPageHeader
        back={{ to: '/app', label: 'Личный кабинет' }}
        eyebrow="Настройки"
        title="Аккаунт"
      >
        {user && (
          <>
            Вы вошли как <span className="text-ink">{user.email}</span>
          </>
        )}
      </AppPageHeader>

      <div className="mt-12 space-y-12">
        <ChangePasswordSection />
        <DeleteAccountSection />
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
      <h2 className="text-ink text-[21px] font-bold">Смена пароля</h2>
      <p className="text-muted mt-2 text-sm">
        После смены пароля активные сессии на других устройствах завершатся.
      </p>

      <form onSubmit={onSubmit} className="mt-6 space-y-4.5">
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
  const [confirming, setConfirming] = useState(false)

  const onDelete = () => {
    setConfirming(false)
    del.mutate(undefined, {
      onSuccess: () => navigate('/', { replace: true }),
    })
  }

  return (
    <section className="border-divider border-t pt-12">
      <h2 className="text-ink text-[21px] font-bold">Удаление аккаунта</h2>
      <p className="text-muted mt-2 max-w-[48ch] text-sm">
        Аккаунт и вся история интервью удаляются безвозвратно. Восстановить их
        будет нельзя.
      </p>

      {del.isError && (
        <div className="mt-5">
          <Alert>{getErrorMessage(del.error)}</Alert>
        </div>
      )}

      <Button
        variant="danger"
        onClick={() => setConfirming(true)}
        disabled={del.isPending}
        className="mt-6"
      >
        {del.isPending ? 'Удаляем…' : 'Удалить аккаунт'}
      </Button>

      <ConfirmDialog
        open={confirming}
        title="Удалить аккаунт?"
        text="Все интервью и отчёты будут потеряны. Действие необратимо."
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </section>
  )
}
