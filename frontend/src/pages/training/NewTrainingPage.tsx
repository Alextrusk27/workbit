import type { FormEvent, KeyboardEvent } from 'react'
import { useId, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Field } from '@/components/ui/Field'
import type {
  NormalizeInputResponse,
  TrainingOptions,
} from '@/features/training/api'
import {
  useCreateSession,
  useNormalizeInput,
  useProfessionSuggest,
  useTopicSuggest,
  useTrainingOptions,
} from '@/features/training/useTraining'
import { getErrorMessage } from '@/lib/api'
import { cn } from '@/lib/cn'
import { useDebounced } from '@/lib/useDebounced'
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

function SuggestField({
  label,
  hint,
  placeholder,
  value,
  suggestions,
  disabled,
  required,
  onChange,
  onPick,
}: {
  label: string
  hint?: string
  placeholder?: string
  value: string
  suggestions: string[]
  disabled?: boolean
  required?: boolean
  onChange: (v: string) => void
  onPick: (v: string) => void
}) {
  const listId = useId()
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)

  const visible = open && suggestions.length > 0

  const pick = (v: string) => {
    onPick(v)
    setOpen(false)
    setActive(-1)
  }

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false)
      setActive(-1)
      return
    }
    if (!visible) return
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((i) => (i + 1) % suggestions.length)
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((i) => (i - 1 + suggestions.length) % suggestions.length)
    } else if (e.key === 'Enter' && active >= 0) {
      e.preventDefault()
      pick(suggestions[active])
    }
  }

  return (
    <div className="relative">
      <Field
        label={label}
        hint={hint}
        placeholder={placeholder}
        value={value}
        disabled={disabled}
        required={required}
        maxLength={100}
        autoComplete="off"
        role="combobox"
        aria-expanded={visible}
        aria-controls={listId}
        aria-autocomplete="list"
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={onKeyDown}
        onChange={(e) => {
          onChange(e.target.value)
          setOpen(true)
          setActive(-1)
        }}
      />

      {visible && (
        <ul
          id={listId}
          role="listbox"
          className="border-rule bg-paper absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border py-1 shadow-lg"
        >
          {suggestions.map((s, i) => (
            <li key={s}>
              <button
                type="button"
                role="option"
                aria-selected={i === active}
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setActive(i)}
                onClick={() => pick(s)}
                className={cn(
                  'block w-full px-3 py-2 text-left text-sm transition-colors',
                  i === active ? 'bg-paper-2 text-ink' : 'text-ink',
                )}
              >
                {s}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function ConfirmPanel({
  result,
  profession,
  topic,
  onPickProfession,
  onPickTopic,
}: {
  result: NormalizeInputResponse
  profession: string
  topic: string
  onPickProfession: (v: string) => void
  onPickTopic: (v: string) => void
}) {
  const rows = [
    {
      key: 'profession',
      label: 'Профессия',
      value: profession,
      recognized: result.professionRecognized,
      suggestions: result.professionSuggestions,
      onPick: onPickProfession,
      unknown: `Не удалось распознать профессию «${profession}». Можно оставить как есть — но вопросы получатся общими.`,
    },
    {
      key: 'topic',
      label: 'Тема',
      value: topic,
      recognized: result.topicRecognized ?? true,
      suggestions: result.topicSuggestions ?? [],
      onPick: onPickTopic,
      unknown: `Не удалось распознать тему «${topic}». Можно оставить как есть.`,
    },
  ].filter((row) => row.value.trim() !== '')

  return (
    <div className="border-rule bg-paper-2/60 rounded-lg border p-5">
      <p className="text-ink text-sm font-medium">Проверьте ввод</p>

      <div className="mt-4 space-y-5">
        {rows.map((row) => (
          <div key={row.key}>
            <p className="text-muted text-xs">
              {row.label}: <span className="text-ink">{row.value}</span>
            </p>
            {!row.recognized && (
              <p className="text-muted mt-1.5 text-xs">{row.unknown}</p>
            )}
            {row.suggestions.length > 0 && (
              <div className="mt-2.5 flex flex-wrap gap-2">
                {row.suggestions.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => row.onPick(s)}
                    className={cn(
                      'touch-manipulation rounded-md border px-3 py-1.5 text-sm transition-colors',
                      'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
                      s === row.value
                        ? 'border-accent bg-accent text-paper'
                        : 'border-rule text-ink hover:border-ink/30 hover:bg-paper',
                    )}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {result.topicFitsProfession === false && (
        <p className="text-muted mt-5 text-xs">
          Тема «{topic}» не выглядит темой для профессии «{profession}» —
          продолжить можно, но вопросы могут получиться неожиданными.
        </p>
      )}
    </div>
  )
}

function TrainingForm({ options }: { options: TrainingOptions }) {
  const navigate = useNavigate()
  const create = useCreateSession()
  const normalize = useNormalizeInput()

  const [profession, setProfession] = useState('')
  const [topic, setTopic] = useState('')
  const [level, setLevel] = useState<string | null>(null)
  const [checked, setChecked] = useState<NormalizeInputResponse | null>(null)

  const professionQuery = useDebounced(profession.trim())
  const topicQuery = useDebounced(topic.trim())
  const professionSuggest = useProfessionSuggest(professionQuery)
  const topicSuggest = useTopicSuggest(profession.trim(), topicQuery)

  const professionOptions =
    professionQuery.length >= 2
      ? (professionSuggest.data ?? [])
      : options.professions
  const topicOptions = topicSuggest.data ?? []

  const inList = (value: string, list: string[]) =>
    list.some((s) => s.toLowerCase() === value.trim().toLowerCase())

  const fromDict =
    inList(profession, professionOptions) &&
    (topic.trim() === '' || inList(topic, topicOptions))

  const ready = profession.trim() !== '' && level !== null

  const start = () => {
    if (level === null) return
    create.mutate(
      {
        profession: profession.trim(),
        topic: topic.trim() === '' ? null : topic.trim(),
        level,
      },
      {
        onSuccess: (session) =>
          navigate(`/app/training/${session.id}`, { replace: true }),
      },
    )
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!ready) return
    if (fromDict || checked) {
      start()
      return
    }
    normalize.mutate(
      {
        profession: profession.trim(),
        topic: topic.trim() === '' ? null : topic.trim(),
      },
      {
        onSuccess: setChecked,
        onError: start,
      },
    )
  }

  const editProfession = (v: string) => {
    setProfession(v)
    setChecked(null)
  }

  const editTopic = (v: string) => {
    setTopic(v)
    setChecked(null)
  }

  const pending = create.isPending || normalize.isPending

  return (
    <form onSubmit={onSubmit} className="mt-8 space-y-8">
      {create.isError && <Alert>{getErrorMessage(create.error)}</Alert>}

      <SuggestField
        label="Профессия"
        hint="Начните вводить — подскажем из справочника; можно вписать свою"
        placeholder="Java-разработчик"
        value={profession}
        suggestions={professionOptions}
        required
        onChange={editProfession}
        onPick={editProfession}
      />

      <SuggestField
        label="Тема (необязательно)"
        hint="Технология или область знаний — без темы вопросы будут общими по профессии"
        placeholder="Spring Boot"
        value={topic}
        suggestions={topicOptions}
        disabled={profession.trim() === ''}
        onChange={editTopic}
        onPick={editTopic}
      />

      <ChipGroup
        label="Уровень"
        options={options.levels}
        value={level}
        onChange={setLevel}
      />

      {checked && (
        <ConfirmPanel
          result={checked}
          profession={profession}
          topic={topic}
          onPickProfession={setProfession}
          onPickTopic={setTopic}
        />
      )}

      <Button type="submit" size="lg" disabled={!ready || pending}>
        {create.isPending
          ? 'Создаём…'
          : normalize.isPending
            ? 'Проверяем…'
            : 'Начать тренировку'}
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
        Назовите профессию, при желании — тему, и выберите уровень. Вопросы
        подберёт рецензент, а разбор придёт в конце.
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
