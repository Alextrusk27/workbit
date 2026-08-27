import type { FormEvent } from 'react'
import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { useAuth } from '@/features/auth/useAuth'
import { motionConfig } from '@/lib/motion'

const LEVELS = [
  { code: 'NOEXP', label: 'Базовый' },
  { code: 'JUNIOR', label: 'Начинающий' },
  { code: 'MIDDLE', label: 'Уверенный' },
  { code: 'SENIOR', label: 'Продвинутый' },
]

const PAIRS: [string, string][] = [
  ['Java-разработчик', 'Многопоточность'],
  ['Продуктовый аналитик', 'SQL и базы данных'],
  ['Бухгалтер', 'Налоговый учёт'],
  ['Frontend-разработчик', 'React'],
  ['Юрист', 'Договорное право'],
]

const TYPE_MS = 80
const ERASE_MS = 35

const fieldLabelClass =
  'text-dim block text-[11px] font-semibold tracking-[0.06em] uppercase'
const fieldInputClass =
  'placeholder:text-dim text-ink w-full bg-transparent text-[15px] font-medium outline-none'

/** Конструктор тренировки в герое /skills-trainer: профессия и навык — свободный
 *  ввод, уровень — дропдаун. Пока пользователь не трогает поля, их заполняет
 *  печатающаяся анимация с примерами пар; фокус отдаёт поля пользователю. */
export function TrainingConstructor() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [profession, setProfession] = useState('')
  const [skill, setSkill] = useState('')
  const [manual, setManual] = useState(false)
  const [level, setLevel] = useState(LEVELS[2])
  const [levelOpen, setLevelOpen] = useState(false)
  const levelRef = useRef<HTMLDivElement>(null)
  const pairIndex = useRef(0)
  const restarted = useRef(false)

  useEffect(() => {
    if (manual || !motionConfig.shouldAnimate()) return
    let timer = 0
    const step = (fn: () => void, ms: number) => {
      timer = window.setTimeout(fn, ms)
    }
    const typeProf = (word: string, i: number) => {
      setProfession(word.slice(0, i))
      if (i < word.length) step(() => typeProf(word, i + 1), TYPE_MS)
      else step(() => typeSkill(PAIRS[pairIndex.current][1], 0), 500)
    }
    const typeSkill = (word: string, i: number) => {
      setSkill(word.slice(0, i))
      if (i < word.length) step(() => typeSkill(word, i + 1), TYPE_MS)
      else step(() => eraseSkill(word, word.length), 2200)
    }
    const eraseSkill = (word: string, i: number) => {
      setSkill(word.slice(0, i))
      if (i > 0) step(() => eraseSkill(word, i - 1), ERASE_MS)
      else {
        const prof = PAIRS[pairIndex.current][0]
        step(() => eraseProf(prof, prof.length), 150)
      }
    }
    const eraseProf = (word: string, i: number) => {
      setProfession(word.slice(0, i))
      if (i > 0) step(() => eraseProf(word, i - 1), ERASE_MS)
      else {
        pairIndex.current = (pairIndex.current + 1) % PAIRS.length
        step(() => typeProf(PAIRS[pairIndex.current][0], 0), 400)
      }
    }
    step(
      () => typeProf(PAIRS[pairIndex.current][0], 0),
      restarted.current ? 1200 : 800,
    )
    restarted.current = true
    return () => window.clearTimeout(timer)
  }, [manual])

  useEffect(() => {
    if (!levelOpen) return
    const onDoc = (e: MouseEvent) => {
      if (!levelRef.current?.contains(e.target as Node)) setLevelOpen(false)
    }
    document.addEventListener('click', onDoc)
    return () => document.removeEventListener('click', onDoc)
  }, [levelOpen])

  const takeOver = () => {
    if (manual) return
    setManual(true)
    setProfession('')
    setSkill('')
  }

  const editProfession = (v: string) => {
    setProfession(v)
    setManual(v !== '' || skill !== '')
  }

  const editSkill = (v: string) => {
    setSkill(v)
    setManual(v !== '' || profession !== '')
  }

  const onFieldBlur = () => {
    if (manual && profession === '' && skill === '') setManual(false)
  }

  const onSubmit = (e: FormEvent) => {
    e.preventDefault()
    const params = new URLSearchParams({ level: level.code })
    if (manual) {
      if (skill.trim() !== '') params.set('skill', skill.trim())
      if (profession.trim() !== '') params.set('profession', profession.trim())
    }
    const to = `/app/training/new?${params.toString()}`
    navigate(isAuthenticated ? to : '/login', {
      state: { from: { pathname: to } },
    })
  }

  return (
    <div className="w-full">
      <form
        onSubmit={onSubmit}
        className="border-line bg-card shadow-pop mx-auto flex max-w-205 flex-col rounded-[14px] border p-2 md:flex-row md:items-stretch"
      >
        <label className="border-line min-w-0 cursor-text px-4 py-2.5 text-left max-md:border-b md:flex-[1.1] md:py-1.5">
          <span className={fieldLabelClass}>Профессия</span>
          <input
            type="text"
            value={profession}
            placeholder="Например, аналитик"
            aria-label="Профессия"
            autoComplete="off"
            maxLength={100}
            onChange={(e) => editProfession(e.target.value)}
            onFocus={takeOver}
            onBlur={onFieldBlur}
            className={fieldInputClass}
          />
        </label>
        <label className="border-line min-w-0 cursor-text px-4 py-2.5 text-left max-md:border-b md:flex-[1.1] md:border-l md:py-1.5">
          <span className={fieldLabelClass}>Навык</span>
          <input
            type="text"
            value={skill}
            placeholder="Например, SQL"
            aria-label="Навык"
            autoComplete="off"
            maxLength={100}
            onChange={(e) => editSkill(e.target.value)}
            onFocus={takeOver}
            onBlur={onFieldBlur}
            className={fieldInputClass}
          />
        </label>
        <div
          ref={levelRef}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setLevelOpen(false)
          }}
          className="border-line relative min-w-0 max-md:border-b md:flex-[0.9] md:border-l"
        >
          <button
            type="button"
            aria-haspopup="listbox"
            aria-expanded={levelOpen}
            onClick={() => setLevelOpen((o) => !o)}
            className="w-full px-4 py-2.5 text-left md:py-1.5"
          >
            <span className={fieldLabelClass}>Уровень</span>
            <span className="text-ink flex items-center justify-between gap-2 text-[15px] font-medium">
              {level.label}
              <svg
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
                strokeLinecap="round"
                strokeLinejoin="round"
                aria-hidden="true"
                className="text-dim size-3 shrink-0"
              >
                <path d="M6 9l6 6 6-6" />
              </svg>
            </span>
          </button>
          {levelOpen && (
            <div
              role="listbox"
              aria-label="Уровень"
              className="border-line bg-pop shadow-pop absolute top-[calc(100%+10px)] left-1.5 z-20 rounded-xl border p-1.5 max-md:right-1.5 md:min-w-50"
            >
              {LEVELS.map((l) => (
                <button
                  key={l.code}
                  type="button"
                  role="option"
                  aria-selected={l.code === level.code}
                  onClick={() => {
                    setLevel(l)
                    setLevelOpen(false)
                  }}
                  className="text-ink hover:bg-indigo/8 hover:text-indigo flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
                >
                  {l.label}
                  {l.code === level.code && (
                    <svg
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2.5"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      aria-hidden="true"
                      className="text-indigo size-3.5 shrink-0"
                    >
                      <path d="M20 6L9 17l-5-5" />
                    </svg>
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
        <button
          type="submit"
          className={buttonClasses({ className: 'max-md:mt-2 md:self-center' })}
        >
          Начать тренировку
        </button>
      </form>
      <p className="text-dim mt-4 text-[13.5px]">
        3 тренировки бесплатно ·{' '}
        <Link
          to="/ai-interview"
          className="text-indigo hover:text-violet transition-colors"
        >
          Или полное интервью по вакансии
        </Link>
      </p>
    </div>
  )
}
