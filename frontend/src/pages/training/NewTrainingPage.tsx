import type { FormEvent, KeyboardEvent } from 'react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import type { TrainingOptions } from '@/features/training/api'
import {
  useCreateSession,
  useTrainingOptions,
} from '@/features/training/useTraining'
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
                'touch-manipulation rounded-md border px-4 py-2 text-sm transition-colors',
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

function TrainingForm({ options }: { options: TrainingOptions }) {
  const navigate = useNavigate()
  const create = useCreateSession()
  const [profession, setProfession] = useState<string | null>(null)
  const [level, setLevel] = useState<string | null>(null)

  const ready = profession !== null && level !== null

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!ready) return
    create.mutate(
      { profession, level },
      {
        onSuccess: (session) =>
          navigate(`/app/training/${session.id}`, { replace: true }),
      },
    )
  }

  return (
    <form onSubmit={onSubmit} className="mt-8 space-y-8">
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

      <Button type="submit" size="lg" disabled={!ready || create.isPending}>
        {create.isPending ? 'Создаём…' : 'Начать тренировку'}
      </Button>
    </form>
  )
}

export function NewTrainingPage() {
  usePageTitle('Новая тренировка')
  const { data: options, isLoading, isError } = useTrainingOptions()

  return (
    <Container className="py-12 sm:py-16">
      <Link
        to="/app/training"
        className="text-accent hover:text-accent-hover text-sm transition-colors"
      >
        ← Тренажёр
      </Link>
      <p className="text-muted mt-8 font-mono text-xs tracking-[0.2em] uppercase">
        Новая тренировка
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Соберём тренировку под вас
      </h1>
      <p className="text-muted mt-4 max-w-xl">
        Выберите профессию и уровень — вопросы соберёт рецензент по ходу
        тренировки, а разбор придёт в конце.
      </p>

      <div className="mt-10 max-w-2xl">
        {isLoading && <p className="text-muted text-sm">Загрузка…</p>}

        {(isError || (!isLoading && !options)) && (
          <Alert>
            Не удалось загрузить параметры тренировки. Обновите страницу.
          </Alert>
        )}

        {options && <TrainingForm options={options} />}
      </div>
    </Container>
  )
}
