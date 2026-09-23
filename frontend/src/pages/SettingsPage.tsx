import { useState } from 'react'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { useAuth, useDeleteAccount } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

const DELETE_WARNING =
  'Аккаунт и вся история интервью и тренировок удаляются безвозвратно. ' +
  'Неиспользованные лимиты сгорают.'

export function SettingsPage() {
  usePageTitle('Настройки')
  const { user } = useAuth()

  return (
    <Container className="max-w-160">
      <AppPageHeader title="Настройки">
        {user && (
          <span>
            Ты вошёл как <span className="text-ink">{user.email}</span>
          </span>
        )}
      </AppPageHeader>

      <div className="mt-10">
        <DeleteAccountSection />
      </div>
    </Container>
  )
}

function DeleteAccountSection() {
  const del = useDeleteAccount()
  const [confirming, setConfirming] = useState(false)

  const onDelete = () => {
    setConfirming(false)
    del.mutate()
  }

  return (
    <section>
      <h2 className="text-ink text-lg font-bold sm:text-[21px]">
        Удаление аккаунта
      </h2>
      <p className="text-muted mt-2 max-w-[48ch] text-sm">{DELETE_WARNING}</p>

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
        text={DELETE_WARNING}
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </section>
  )
}
