import type { FormEvent, KeyboardEvent } from 'react'
import { useId, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
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
  useSkillSuggest,
  useTrainingOptions,
} from '@/features/training/useTraining'
import { trainingErrorMessage } from '@/features/training/errors'
import { cn } from '@/lib/cn'
import { useDebounced } from '@/lib/useDebounced'
import { usePageTitle } from '@/lib/usePageTitle'

const LEVEL_CODES = ['NOEXP', 'JUNIOR', 'MIDDLE', 'SENIOR']

const fromQuery = (value: string | null) => (value ?? '').slice(0, 100)

const pillClass = (selected: boolean) =>
  cn(
    'touch-manipulation rounded-full border px-4 py-[7px] text-[13.5px] font-medium transition',
    'focus-visible:outline-indigo focus-visible:outline-2 focus-visible:outline-offset-2',
    selected
      ? 'border-indigo/35 bg-indigo/12 text-ink'
      : 'border-line text-muted hover:border-glass-line hover:text-ink',
  )

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
      <legend className="text-muted text-[13.5px] font-semibold">
        {label}
      </legend>
      <div className="mt-3 flex flex-wrap gap-2" role="radiogroup">
        {options.map((opt, i) => (
          <button
            key={opt}
            type="button"
            role="radio"
            aria-checked={opt === value}
            tabIndex={i === activeIndex ? 0 : -1}
            onClick={() => onChange(opt)}
            onKeyDown={(e) => onKeyDown(e, i)}
            className={pillClass(opt === value)}
          >
            {opt}
          </button>
        ))}
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
          className="border-line bg-pop shadow-pop absolute z-20 mt-1.5 max-h-60 w-full overflow-auto rounded-lg border p-1.5"
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
                  'text-ink block w-full rounded-sm px-3 py-2 text-left text-sm transition-colors',
                  i === active && 'bg-glass',
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
  skill,
  profession,
  onPickSkill,
  onPickProfession,
}: {
  result: NormalizeInputResponse
  skill: string
  profession: string
  onPickSkill: (v: string) => void
  onPickProfession: (v: string) => void
}) {
  const rows = [
    {
      key: 'skill',
      label: 'Навык',
      value: skill,
      recognized: result.skillRecognized,
      suggestions: result.skillSuggestions,
      onPick: onPickSkill,
      unknown: `Не удалось распознать навык «${skill}». С таким навыком тренировка не запустится — уточни формулировку или выбери вариант.`,
    },
    {
      key: 'profession',
      label: 'Профессия',
      value: profession,
      recognized: result.professionRecognized,
      suggestions: result.professionSuggestions,
      onPick: onPickProfession,
      unknown: `Не удалось распознать профессию «${profession}». С такой профессией тренировка не запустится — уточни формулировку или выбери вариант.`,
    },
  ].filter((row) => row.value.trim() !== '')

  return (
    <div className="border-line bg-glass rounded-xl border p-5">
      <p className="text-ink text-sm font-semibold">Проверь ввод</p>

      <div className="mt-4 space-y-5">
        {rows.map((row) => (
          <div key={row.key}>
            <p className="text-muted text-xs">
              {row.label}: <span className="text-ink">{row.value}</span>
            </p>
            {!row.recognized && (
              <p className="text-dim mt-1.5 text-xs">{row.unknown}</p>
            )}
            {row.suggestions.length > 0 && (
              <div className="mt-2.5 flex flex-wrap gap-2">
                {row.suggestions.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => row.onPick(s)}
                    className={pillClass(s === row.value)}
                  >
                    {s}
                  </button>
                ))}
              </div>
            )}
          </div>
        ))}
      </div>

      {!result.skillFitsProfession && (
        <p className="text-dim mt-5 text-xs">
          Навык «{skill}» не выглядит навыком профессии «{profession}» —
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
  const [searchParams] = useSearchParams()

  const [skill, setSkill] = useState(fromQuery(searchParams.get('skill')))
  const [profession, setProfession] = useState(
    fromQuery(searchParams.get('profession')),
  )
  const [level, setLevel] = useState<string | null>(
    options.levels[LEVEL_CODES.indexOf(searchParams.get('level') ?? '')] ??
      null,
  )
  const [checked, setChecked] = useState<NormalizeInputResponse | null>(null)

  const skillQuery = useDebounced(skill.trim())
  const professionQuery = useDebounced(profession.trim())
  const skillSuggest = useSkillSuggest(profession.trim(), skillQuery)
  const professionSuggest = useProfessionSuggest(professionQuery)

  const skillOptions =
    skillQuery.length >= 2 ? (skillSuggest.data ?? []) : options.skills
  const professionOptions =
    professionQuery.length >= 2
      ? (professionSuggest.data ?? [])
      : options.professions

  const inList = (value: string, list: string[]) =>
    list.some((s) => s.toLowerCase() === value.trim().toLowerCase())

  const fromDict =
    inList(skill, skillOptions) && inList(profession, professionOptions)

  const ready =
    skill.trim() !== '' && profession.trim() !== '' && level !== null

  const blocked =
    checked !== null &&
    ((!checked.skillRecognized && !inList(skill, checked.skillSuggestions)) ||
      (!checked.professionRecognized &&
        !inList(profession, checked.professionSuggestions)))

  const start = () => {
    if (level === null) return
    create.mutate(
      {
        skill: skill.trim(),
        profession: profession.trim(),
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
    if (!ready || blocked) return
    if (fromDict || checked) {
      start()
      return
    }
    normalize.mutate(
      {
        skill: skill.trim(),
        profession: profession.trim(),
      },
      {
        onSuccess: setChecked,
        onError: start,
      },
    )
  }

  const editSkill = (v: string) => {
    setSkill(v)
    setChecked(null)
  }

  const editProfession = (v: string) => {
    setProfession(v)
    setChecked(null)
  }

  const pending = create.isPending || normalize.isPending

  return (
    <form onSubmit={onSubmit} className="mt-10 max-w-160 space-y-6">
      {create.isError && <Alert>{trainingErrorMessage(create.error)}</Alert>}

      <SuggestField
        label="Навык"
        hint="Технология, область знаний или умение — по нему и будут вопросы"
        placeholder="Spring Boot"
        value={skill}
        suggestions={skillOptions}
        required
        onChange={editSkill}
        onPick={editSkill}
      />

      <SuggestField
        label="Профессия"
        hint="Уточняет, под каким углом смотреть на навык"
        placeholder="Java-разработчик"
        value={profession}
        suggestions={professionOptions}
        required
        onChange={editProfession}
        onPick={editProfession}
      />

      <ChipGroup
        label="Уровень сложности"
        options={options.levels}
        value={level}
        onChange={setLevel}
      />

      {checked && (
        <ConfirmPanel
          result={checked}
          skill={skill}
          profession={profession}
          onPickSkill={setSkill}
          onPickProfession={setProfession}
        />
      )}

      <Button type="submit" disabled={!ready || blocked || pending}>
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
    <Container>
      <AppPageHeader
        back={{ to: '/app/training', label: 'Тренажёр' }}
        eyebrow="Новая тренировка навыка"
        title="Соберём тренировку под тебя"
      >
        Тренировка — это прокачка одного навыка. Укажи навык, профессию, в
        контексте которой он нужен, и выбери уровень сложности. Вопросы подберёт
        рецензент, а разбор придёт в конце.
      </AppPageHeader>

      {isLoading && <p className="text-muted mt-10 text-sm">Загрузка…</p>}

      {(isError || (!isLoading && !options)) && (
        <div className="mt-10 max-w-160">
          <Alert>
            Не удалось загрузить параметры тренировки. Обнови страницу.
          </Alert>
        </div>
      )}

      {options && <TrainingForm options={options} />}
    </Container>
  )
}
