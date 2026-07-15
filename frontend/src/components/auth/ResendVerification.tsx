import { useEffect, useState } from 'react'
import { Alert } from '@/components/ui/Alert'
import { authErrorMessage } from '@/features/auth/errors'
import { useResendVerification } from '@/features/auth/useAuth'

const COOLDOWN_SECONDS = 60

/** Кнопка «Не получили письмо?» с кулдауном перед повторной отправкой.
 *  initialCooldown > 0 — если письмо только что уже ушло (напр. при регистрации). */
export function ResendVerification({
  email,
  initialCooldown = 0,
}: {
  email: string
  initialCooldown?: number
}) {
  const resend = useResendVerification()
  const [cooldown, setCooldown] = useState(initialCooldown)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const onResend = () => {
    resend.mutate(email, { onSuccess: () => setCooldown(COOLDOWN_SECONDS) })
  }

  const label = resend.isPending
    ? 'Отправляем…'
    : cooldown > 0
      ? `Отправить письмо заново через ${cooldown} с`
      : 'Не получили письмо? Отправить заново'

  return (
    <div className="mt-4">
      <button
        type="button"
        onClick={onResend}
        disabled={cooldown > 0 || resend.isPending}
        className="text-accent hover:text-accent-hover text-sm transition-colors disabled:opacity-60"
      >
        {label}
      </button>
      {resend.isSuccess && (
        <p className="text-muted mt-1 text-xs">
          Письмо отправлено. Проверьте почту.
        </p>
      )}
      {resend.isError && (
        <div className="mt-2">
          <Alert>{authErrorMessage(resend.error)}</Alert>
        </div>
      )}
    </div>
  )
}
