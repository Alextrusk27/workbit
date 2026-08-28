import type { FormEvent } from 'react'
import { useEffect, useState } from 'react'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { authErrorMessage } from '@/features/auth/errors'
import { useRequestCode, useVerifyCode } from '@/features/auth/useAuth'

const COOLDOWN_SECONDS = 60

/** Ввод шестизначного кода из письма и повторная отправка кода с кулдауном.
 *  Кулдаун стартует сразу (код только что ушёл на почту) и перезапускается
 *  даже на отказ — иначе кнопка долбит лимит бэка. */
export function CodeForm({
  email,
  onSuccess,
}: {
  email: string
  onSuccess: () => void
}) {
  const verify = useVerifyCode()
  const resend = useRequestCode()
  const [code, setCode] = useState('')
  const [cooldown, setCooldown] = useState(COOLDOWN_SECONDS)

  useEffect(() => {
    if (cooldown <= 0) return
    const timer = setTimeout(() => setCooldown((c) => c - 1), 1000)
    return () => clearTimeout(timer)
  }, [cooldown])

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    try {
      await verify.mutateAsync({ email, code })
      onSuccess()
    } catch {
      // ошибка показывается через verify.isError
    }
  }

  const onResend = () => {
    resend.mutate(
      { email, personalDataConsent: true },
      {
        onSuccess: () => {
          setCooldown(COOLDOWN_SECONDS)
          setCode('')
        },
        onError: () => setCooldown(COOLDOWN_SECONDS),
      },
    )
  }

  const resendLabel = resend.isPending
    ? 'Отправляем…'
    : cooldown > 0
      ? `Отправить код ещё раз через ${cooldown} с`
      : 'Отправить код ещё раз'

  return (
    <>
      <form onSubmit={onSubmit} className="mt-8.5 space-y-5.5">
        {verify.isError && <Alert>{authErrorMessage(verify.error)}</Alert>}
        <Field
          className="[&>input]:text-base [&>label]:text-sm"
          label="Код из письма"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          pattern="\d{6}"
          required
          style={{ letterSpacing: '0.2em' }}
          value={code}
          onChange={(e) => setCode(e.target.value)}
        />
        <Button
          type="submit"
          size="lg"
          className="w-full"
          disabled={verify.isPending}
        >
          {verify.isPending ? 'Проверяем…' : 'Продолжить'}
        </Button>
      </form>

      <div className="mt-4">
        <button
          type="button"
          onClick={onResend}
          disabled={cooldown > 0 || resend.isPending}
          className="text-indigo hover:text-violet py-2 text-sm transition-colors disabled:opacity-60 lg:py-0"
        >
          {resendLabel}
        </button>
        {resend.isSuccess && cooldown > 0 && (
          <p className="text-muted mt-1 text-xs">
            Новый код отправлен. Проверь почту.
          </p>
        )}
        {resend.isError && (
          <div className="mt-2">
            <Alert>{authErrorMessage(resend.error)}</Alert>
          </div>
        )}
      </div>
    </>
  )
}
