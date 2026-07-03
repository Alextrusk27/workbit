import type { FormEvent, KeyboardEvent } from 'react'
import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import {
  useCreateSession,
  useInterviewOptions,
} from '@/features/interview/useInterview'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { usePageTitle } from '@/lib/usePageTitle'

function ChipGroup({
  label,
  options,
  value,
  onChange,
}: {
  label: string
  options: string[]
  value: string | null
  onChange: (v: string) => void
}) {
  const activeIndex = Math.max(0, value ? options.indexOf(value) : 0)

  const onKeyDown = (e: KeyboardEvent<HTMLButtonElement>, i: number) => {
    const forward = e.key === 'ArrowRight' || e.key === 'ArrowDown'
    const back = e.key === 'ArrowLeft' || e.key === 'ArrowUp'
    if (!forward && !back) return
    e.preventDefault()
    const next = forward
      ? (i + 1) % options.length
      : (i - 1 + options.length) % options.length
    onChange(options[next])
    const group = e.currentTarget.parentElement
    ;(group?.children[next] as HTMLElement | undefined)?.focus()
  }

  return (
    <fieldset>
      <legend className="text-ink text-sm font-medium">{label}</legend>
      <div className="mt-3 flex flex-wrap gap-2" role="radiogroup">
        {options.map((opt, i) => {
          const selected = opt === value
          return (
            <button
              key={opt}
              type="button"
              role="radio"
              aria-checked={selected}
              tabIndex={i === activeIndex ? 0 : -1}
              onClick={() => onChange(opt)}
              onKeyDown={(e) => onKeyDown(e, i)}
              className={cn(
                'rounded-md border px-4 py-2 text-sm transition-colors touch-manipulation',
                'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
                selected
                  ? 'border-accent bg-accent text-paper'
                  : 'border-rule text-ink hover:border-ink/30 hover:bg-paper-2',
              )}
            >
              {opt}
            </button>
          )
        })}
      </div>
    </fieldset>
  )
}

export function NewInterviewPage() {
  usePageTitle('Новое интервью')
  const navigate = useNavigate()
  const { data: options, isLoading, isError } = useInterviewOptions()
  const create = useCreateSession()

  const [profession, setProfession] = useState<string | null>(null)
  const [level, setLevel] = useState<string | null>(null)
  const [companyType, setCompanyType] = useState<string | null>(null)
  const [total, setTotal] = useState<number | null>(null)

  useEffect(() => {
    if (options && total === null) setTotal(options.minQuestions)
  }, [options, total])

  const ready =
    profession !== null &&
    level !== null &&
    companyType !== null &&
    total !== null

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!ready) return
    create.mutate(
      { profession, level, companyType, totalQuestions: total },
      {
        onSuccess: (session) =>
          navigate(`/app/interview/${session.id}`, { replace: true }),
      },
    )
  }

  if (isLoading) {
    return (
      <Container className="py-16">
        <p className="text-muted text-sm">Загрузка…</p>
      </Container>
    )
  }

  if (isError || !options) {
    return (
      <Container className="py-16">
        <Alert>
          Не удалось загрузить параметры интервью. Обновите страницу.
        </Alert>
      </Container>
    )
  }

  return (
    <Container className="py-12 sm:py-16">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Новое интервью
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Соберём тренировку под вас
      </h1>
      <p className="text-muted mt-4 max-w-xl">
        Выберите профессию, уровень и тип компании — подберём вопросы под этот
        контекст.
      </p>

      <form onSubmit={onSubmit} className="mt-10 max-w-2xl space-y-8">
        {create.isError && <Alert>{getErrorMessage(create.error)}</Alert>}

        <ChipGroup
          label="Профессия"
          options={options.professions}
          value={profession}
          onChange={setProfession}
        />
        <ChipGroup
          label="Уровень"
          options={options.levels}
          value={level}
          onChange={setLevel}
        />
        <ChipGroup
          label="Тип компании"
          options={options.companyTypes}
          value={companyType}
          onChange={setCompanyType}
        />

        <div>
          <label
            htmlFor="total"
            className="text-ink flex items-baseline justify-between text-sm font-medium"
          >
            <span>Количество вопросов</span>
            <span className="text-accent font-mono text-base">{total}</span>
          </label>
          <input
            id="total"
            type="range"
            min={options.minQuestions}
            max={options.maxQuestions}
            value={total ?? options.minQuestions}
            onChange={(e) => setTotal(Number(e.target.value))}
            className="accent-accent mt-3 w-full"
          />
          <div className="text-muted mt-1 flex justify-between text-xs">
            <span>{options.minQuestions}</span>
            <span>{options.maxQuestions}</span>
          </div>
        </div>

        <Button type="submit" size="lg" disabled={!ready || create.isPending}>
          {create.isPending ? 'Создаём…' : 'Начать интервью'}
        </Button>
      </form>
    </Container>
  )
}
