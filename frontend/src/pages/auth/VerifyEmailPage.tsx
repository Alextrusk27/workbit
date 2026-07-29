import { useEffect, useRef } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { useVerifyEmail } from '@/features/auth/useAuth'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function VerifyEmailPage() {
  usePageTitle('Подтверждение почты')
  const [params] = useSearchParams()
  const token = params.get('token')
  const verify = useVerifyEmail()
  const navigate = useNavigate()
  const started = useRef(false)

  // Токен подтверждения одноразовый — шлём ровно один раз (ref переживает
  // двойной прогон эффектов в StrictMode). На успехе кука уже установлена и
  // профиль перечитан — в ЛК.
  useEffect(() => {
    if (!token || started.current) return
    started.current = true
    verify
      .mutateAsync(token)
      .then(() => navigate('/app', { replace: true }))
      .catch(() => {
        // ошибка показывается через verify.isError
      })
  }, [token, verify, navigate])

  if (!token) {
    return (
      <>
        <h1 className="text-ink text-[28px]">Ссылка неполная</h1>
        <p className="text-muted mt-3 text-sm">
          В ссылке нет токена подтверждения. Откройте её из письма целиком.
        </p>
        <p className="mt-8 text-sm">
          <Link to="/login" className="text-indigo hover:text-violet">
            Вернуться ко входу
          </Link>
        </p>
      </>
    )
  }

  if (verify.isError) {
    return (
      <>
        <h1 className="text-ink text-[28px]">Не удалось подтвердить</h1>
        <div className="mt-4">
          <Alert>{getErrorMessage(verify.error)}</Alert>
        </div>
        <p className="text-muted mt-4 text-sm">
          Ссылка могла устареть. Попробуйте войти — мы предложим отправить
          подтверждение заново.
        </p>
        <p className="mt-8 text-sm">
          <Link to="/login" className="text-indigo hover:text-violet">
            Ко входу
          </Link>
        </p>
      </>
    )
  }

  return <p className="text-muted text-sm">Подтверждаем почту…</p>
}
