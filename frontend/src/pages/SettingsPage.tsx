import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { useAuth, useDeleteAccount } from '@/features/auth/useAuth'
import { PLAN_LABELS } from '@/features/billing/labels'
import { useQuota } from '@/features/billing/useBilling'
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

      <div className="mt-12">
        <PlanSection />
      </div>

      <div className="mt-12">
        <DeleteAccountSection />
      </div>
    </Container>
  )
}

function PlanSection() {
  const { data, isLoading } = useQuota()

  return (
    <section>
      <h2 className="text-ink text-[21px] font-bold">Тариф</h2>

      {isLoading && (
        <div role="status" className="mt-4">
          <span className="sr-only">Загрузка тарифа…</span>
          <Skeleton className="h-5 w-40" />
          <Skeleton className="mt-3 h-4 w-64" />
        </div>
      )}

      {data && (
        <>
          <p className="text-ink mt-4 font-semibold">
            {PLAN_LABELS[data.plan]}
            {data.planExpiresAt && (
              <span className="text-muted font-normal">
                {' '}
                · действует до{' '}
                {new Date(data.planExpiresAt).toLocaleDateString('ru-RU', {
                  day: 'numeric',
                  month: 'long',
                  year: 'numeric',
                })}
              </span>
            )}
          </p>
          <dl className="text-muted mt-3 max-w-[48ch] text-sm">
            <div className="flex justify-between gap-4">
              <dt>По подписке</dt>
              <dd className="tabular-nums">
                интервью: {data.planInterviewsLeft}, тренировок:{' '}
                {data.planTrainingsLeft}
              </dd>
            </div>
            <div className="mt-1.5 flex justify-between gap-4">
              <dt>Пакеты</dt>
              <dd className="tabular-nums">
                интервью: {data.packInterviewsLeft}, тренировок:{' '}
                {data.packTrainingsLeft}
              </dd>
            </div>
          </dl>
          <p className="text-dim mt-3 text-sm">
            Сменить тариф или докупить пакет можно на странице{' '}
            <Link
              to="/pricing"
              className="text-indigo hover:text-violet transition-colors"
            >
              тарифов
            </Link>
            .
          </p>
        </>
      )}
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
    <section>
      <h2 className="text-ink text-[21px] font-bold">Удаление аккаунта</h2>
      <p className="text-muted mt-2 max-w-[48ch] text-sm">
        Аккаунт и вся история интервью и тренировок удаляются безвозвратно.
        Восстановить их будет нельзя.
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
        text="Все интервью, тренировки и отчёты будут потеряны. Действие необратимо."
        onConfirm={onDelete}
        onClose={() => setConfirming(false)}
      />
    </section>
  )
}
