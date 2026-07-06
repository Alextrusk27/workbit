import type { FormEvent } from 'react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Checkbox } from '@/components/ui/Checkbox'
import { Field } from '@/components/ui/Field'
import { useRegister } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function RegisterPage() {
  usePageTitle('Регистрация')
  const register = useRegister()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [acceptTerms, setAcceptTerms] = useState(false)
  const [acceptPrivacy, setAcceptPrivacy] = useState(false)
  const [consentError, setConsentError] = useState(false)
  const consentRef = useRef<HTMLDivElement>(null)

  const consentGiven = acceptTerms && acceptPrivacy

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!consentGiven) {
      setConsentError(true)
      consentRef.current
        ?.querySelector<HTMLInputElement>('input:not(:checked)')
        ?.focus()
      return
    }
    setConsentError(false)
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

        <div ref={consentRef} className="space-y-3">
          <Checkbox checked={acceptTerms} onChange={setAcceptTerms} required>
            Принимаю{' '}
            <Link
              to="/terms"
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent hover:text-accent-hover underline underline-offset-2"
            >
              Условия сервиса
            </Link>
          </Checkbox>
          <Checkbox
            checked={acceptPrivacy}
            onChange={setAcceptPrivacy}
            required
          >
            Даю согласие на обработку персональных данных в соответствии с{' '}
            <Link
              to="/privacy"
              target="_blank"
              rel="noopener noreferrer"
              className="text-accent hover:text-accent-hover underline underline-offset-2"
            >
              Политикой конфиденциальности
            </Link>
          </Checkbox>
          {consentError && !consentGiven && (
            <p role="alert" className="text-accent text-sm">
              Отметьте оба пункта, чтобы продолжить.
            </p>
          )}
        </div>

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
