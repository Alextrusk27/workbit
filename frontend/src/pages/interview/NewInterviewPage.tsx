import type { FormEvent, KeyboardEvent } from 'react'
import { useId, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Field } from '@/components/ui/Field'
import { Skeleton } from '@/components/ui/Skeleton'
import type { InterviewOptions } from '@/features/interview/api'
import {
  useCreateSession,
  useCreateSessionByVacancy,
  useInterviewOptions,
} from '@/features/interview/useInterview'
import {
  isHhVacancyUrl,
  useVacancyPreview,
} from '@/features/vacancy/useVacancy'
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

function Segmented<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { value: T; label: string }[]
  value: T
  onChange: (v: T) => void
}) {
  return (
    <div
      role="tablist"
      className="border-rule inline-flex rounded-lg border p-1"
    >
      {options.map((opt) => {
        const selected = opt.value === value
        return (
          <button
            key={opt.value}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(opt.value)}
            className={cn(
              'touch-manipulation rounded-md px-4 py-2 text-sm transition-colors',
              'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
              selected ? 'bg-accent text-paper' : 'text-muted hover:text-ink',
            )}
          >
            {opt.label}
          </button>
        )
      })}
    </div>
  )
}

function QuestionCountSlider({
  min,
  max,
  value,
  onChange,
}: {
  min: number
  max: number
  value: number
  onChange: (v: number) => void
}) {
  const id = useId()
  return (
    <div>
      <label
        htmlFor={id}
        className="text-ink flex items-baseline justify-between text-sm font-medium"
      >
        <span>Количество вопросов</span>
        <span className="text-accent font-mono text-base">{value}</span>
      </label>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="accent-accent mt-3 w-full"
      />
      <div className="text-muted mt-1 flex justify-between text-xs">
        <span>{min}</span>
        <span>{max}</span>
      </div>
    </div>
  )
}

function CatalogForm({
  options,
  onCreated,
}: {
  options: InterviewOptions
  onCreated: (sessionId: string) => void
}) {
  const create = useCreateSession()
  const [profession, setProfession] = useState<string | null>(null)
  const [level, setLevel] = useState<string | null>(null)
  const [companyType, setCompanyType] = useState<string | null>(null)
  const [total, setTotal] = useState(options.minQuestions)

  const ready = profession !== null && level !== null && companyType !== null

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!ready) return
    create.mutate(
      { profession, level, companyType, totalQuestions: total },
      { onSuccess: (session) => onCreated(session.id) },
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
      <ChipGroup
        label="Тип компании"
        options={options.companyTypes}
        value={companyType}
        onChange={setCompanyType}
      />
      <QuestionCountSlider
        min={options.minQuestions}
        max={options.maxQuestions}
        value={total}
        onChange={setTotal}
      />

      <Button type="submit" size="lg" disabled={!ready || create.isPending}>
        {create.isPending ? 'Создаём…' : 'Начать интервью'}
      </Button>
    </form>
  )
}

function VacancyPreviewCard({ url }: { url: string }) {
  const { data, isFetching, isError, error } = useVacancyPreview(url)

  if (isFetching) {
    return (
      <div role="status" className="border-rule rounded-lg border p-4">
        <span className="sr-only">Загрузка вакансии…</span>
        <Skeleton className="h-5 w-52" />
        <Skeleton className="mt-2 h-4 w-36" />
        <Skeleton className="mt-3 h-4 w-44" />
      </div>
    )
  }

  if (isError) {
    return <Alert>{getErrorMessage(error)}</Alert>
  }

  if (!data) return null

  return (
    <div className="border-rule bg-paper-2/60 rounded-lg border p-4">
      <p className="text-ink font-display text-lg leading-snug">{data.name}</p>
      <p className="text-muted mt-1 text-sm">{data.employer}</p>
      <div className="text-muted mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs">
        {data.salary && <span>{data.salary}</span>}
        {data.experience && <span>Опыт: {data.experience}</span>}
      </div>
    </div>
  )
}

function VacancyForm({
  options,
  onCreated,
}: {
  options: InterviewOptions
  onCreated: (sessionId: string) => void
}) {
  const create = useCreateSessionByVacancy()
  const [url, setUrl] = useState('')
  const [total, setTotal] = useState(options.minQuestions)

  const urlValid = isHhVacancyUrl(url)

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!urlValid) return
    create.mutate(
      { vacancyUrl: url.trim(), totalQuestions: total },
      { onSuccess: (session) => onCreated(session.id) },
    )
  }

  return (
    <form onSubmit={onSubmit} className="mt-8 space-y-6">
      {create.isError && <Alert>{getErrorMessage(create.error)}</Alert>}

      <div className="space-y-4">
        <Field
          label="Ссылка на вакансию hh.ru"
          type="url"
          inputMode="url"
          placeholder="https://hh.ru/vacancy/123456"
          hint={
            url && !urlValid
              ? 'Ссылка должна вести на вакансию hh.ru (https://hh.ru/vacancy/…)'
              : 'Подтянем название, работодателя и требуемый опыт'
          }
          value={url}
          onChange={(e) => setUrl(e.target.value)}
        />
        {urlValid && <VacancyPreviewCard url={url} />}
      </div>

      <QuestionCountSlider
        min={options.minQuestions}
        max={options.maxQuestions}
        value={total}
        onChange={setTotal}
      />

      <Button type="submit" size="lg" disabled={!urlValid || create.isPending}>
        {create.isPending ? 'Собираем вопросы…' : 'Начать интервью'}
      </Button>
      {create.isPending && (
        <p className="text-muted text-xs">
          Готовим вопросы под вакансию — это может занять несколько секунд.
        </p>
      )}
    </form>
  )
}

export function NewInterviewPage() {
  usePageTitle('Новое интервью')
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { data: options, isLoading, isError } = useInterviewOptions()
  const [mode, setMode] = useState<'catalog' | 'vacancy'>(
    searchParams.get('mode') === 'vacancy' ? 'vacancy' : 'catalog',
  )

  const onCreated = (sessionId: string) =>
    navigate(`/app/interview/${sessionId}`, { replace: true })

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
      <Link
        to="/app"
        className="text-accent hover:text-accent-hover text-sm transition-colors"
      >
        ← Мои интервью
      </Link>
      <p className="text-muted mt-8 font-mono text-xs tracking-[0.2em] uppercase">
        Новое интервью
      </p>
      <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
        Соберём интервью под вас
      </h1>
      <p className="text-muted mt-4 max-w-xl">
        Тренировка — вопросы под профессию, уровень и тип компании. Под вакансию
        — вопросы под конкретную вакансию с hh.ru.
      </p>

      <div className="mt-10 max-w-2xl">
        <Segmented
          options={[
            { value: 'catalog', label: 'Тренировка' },
            { value: 'vacancy', label: 'Под вакансию' },
          ]}
          value={mode}
          onChange={setMode}
        />

        {mode === 'catalog' ? (
          <CatalogForm options={options} onCreated={onCreated} />
        ) : (
          <VacancyForm options={options} onCreated={onCreated} />
        )}
      </div>
    </Container>
  )
}
