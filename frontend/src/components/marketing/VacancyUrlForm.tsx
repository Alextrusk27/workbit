import type { FormEvent } from 'react'
import { useId, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { IconLink } from '@/components/marketing/icons'
import { useAuth } from '@/features/auth/useAuth'
import { savePendingVacancyUrl } from '@/features/vacancy/pendingVacancy'
import { isHhVacancyUrl } from '@/features/vacancy/useVacancy'

/** Форма ссылки на вакансию hh.ru: валидная ссылка сохраняется в
 *  pendingVacancy и подхватывается формой создания интервью после входа.
 *  `hero` — пилюля героя /ai-interview, `card` — ряд для карточки на главной. */
export function VacancyUrlForm({
  variant = 'hero',
}: {
  variant?: 'hero' | 'card'
}) {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const hintId = useId()

  const [url, setUrl] = useState('')
  const [showHint, setShowHint] = useState(false)

  const onUrlChange = (value: string) => {
    setUrl(value)
    if (showHint) setShowHint(false)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const trimmed = url.trim()
    if (trimmed !== '' && !isHhVacancyUrl(trimmed)) {
      setShowHint(true)
      return
    }
    if (trimmed !== '') savePendingVacancyUrl(trimmed)
    const startTo = isAuthenticated ? '/app/interview/new' : '/login'
    navigate(startTo, { state: { from: { pathname: '/app/interview/new' } } })
  }

  const inputProps = {
    type: 'url',
    inputMode: 'url',
    autoComplete: 'off',
    placeholder: 'https://hh.ru/vacancy/123456',
    'aria-label': 'Ссылка на вакансию hh.ru',
    'aria-invalid': showHint || undefined,
    'aria-describedby': showHint ? hintId : undefined,
    value: url,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) =>
      onUrlChange(e.target.value),
  } as const

  return (
    <>
      {variant === 'hero' ? (
        <form
          noValidate
          onSubmit={onSubmit}
          className="border-line bg-card shadow-pop mx-auto flex max-w-160 flex-col gap-2.5 rounded-[14px] border p-2 sm:flex-row sm:items-center sm:pl-4.5"
        >
          <span className="flex min-w-0 items-center gap-2.5 px-2.5 pt-1.5 sm:flex-1 sm:px-0 sm:pt-0">
            <IconLink className="text-dim size-[18px] shrink-0" />
            <input
              {...inputProps}
              className="placeholder:text-dim text-ink w-full min-w-0 bg-transparent text-left text-[15px] outline-none"
            />
          </span>
          <button type="submit" className={buttonClasses()}>
            Пройти пробное интервью
          </button>
        </form>
      ) : (
        <form
          noValidate
          onSubmit={onSubmit}
          className="flex flex-col gap-2.5 sm:flex-row"
        >
          <input
            {...inputProps}
            className="border-line bg-card text-ink placeholder:text-dim h-11 w-full min-w-0 rounded-[10px] border px-3.5 text-[13.5px] sm:flex-1"
          />
          <button type="submit" className={buttonClasses()}>
            Пройти пробное интервью
          </button>
        </form>
      )}
      {showHint && (
        <p id={hintId} className="text-dim mt-3 text-[12.5px]">
          Вставь прямую ссылку на вакансию hh.ru вида
          https://hh.ru/vacancy/123456.
        </p>
      )}
    </>
  )
}
