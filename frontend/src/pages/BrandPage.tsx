import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Field } from '@/components/ui/Field'
import { MarginNote } from '@/components/ui/MarginNote'
import { PlanCard } from '@/components/ui/PlanCard'
import { usePageTitle } from '@/lib/usePageTitle'

interface Token {
  name: string
  role: string
  /** Тёмный образец — светлый текст поверх; иначе тёмный. */
  dark?: boolean
}

const PALETTE: Token[] = [
  { name: 'paper', role: 'Бумага — фон страницы' },
  { name: 'paper-2', role: 'Поверхности, карточки' },
  { name: 'ink', role: 'Основной текст', dark: true },
  { name: 'muted', role: 'Вторичный текст', dark: true },
  { name: 'rule', role: 'Тонкие линии, границы' },
  { name: 'accent', role: 'Акцент — петролёвый синий', dark: true },
  { name: 'accent-hover', role: 'Ховер акцента', dark: true },
  { name: 'pine', role: 'Вторичный акцент — хвоя', dark: true },
  { name: 'edit', role: '«Синий карандаш» — пометки рецензента', dark: true },
]

const TOKEN_NAMES = PALETTE.map((t) => t.name)

/** Читает вычисленные значения CSS-переменных — токены остаются источником истины. */
function useTokenHex(names: string[]): Record<string, string> {
  const [hex, setHex] = useState<Record<string, string>>({})
  useEffect(() => {
    const styles = getComputedStyle(document.documentElement)
    const next: Record<string, string> = {}
    for (const n of names) {
      next[n] = styles.getPropertyValue(`--color-${n}`).trim().toUpperCase()
    }
    setHex(next)
  }, [names])
  return hex
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-rule border-t py-14">
      <h2 className="text-ink font-display text-2xl">{title}</h2>
      <div className="mt-8">{children}</div>
    </section>
  )
}

export function BrandPage() {
  usePageTitle('Брендбук')
  const hex = useTokenHex(TOKEN_NAMES)

  return (
    <Container className="py-12 sm:py-16">
      <Link
        to="/"
        className="text-accent hover:text-accent-hover text-sm transition-colors"
      >
        ← На главную
      </Link>

      <header className="mt-8">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Брендбук
        </p>
        <h1 className="text-ink mt-4 text-4xl sm:text-5xl">
          Спокойный эксперт
        </h1>
        <p className="text-muted mt-5 max-w-xl text-lg">
          Живой справочник дизайн-системы: палитра, типографика и компоненты.
          Значения читаются из токенов в{' '}
          <code className="font-mono text-sm">src/index.css</code> — источника
          истины.
        </p>
      </header>

      <Section title="Палитра">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PALETTE.map((t) => (
            <div
              key={t.name}
              className="border-rule overflow-hidden rounded-lg border"
            >
              <div
                className="flex h-24 items-end p-3"
                style={{ backgroundColor: `var(--color-${t.name})` }}
              >
                <span
                  className={`font-mono text-xs ${t.dark ? 'text-paper' : 'text-ink'}`}
                >
                  {hex[t.name] ?? '…'}
                </span>
              </div>
              <div className="p-3">
                <p className="text-ink font-mono text-sm">--color-{t.name}</p>
                <p className="text-muted mt-1 text-xs">{t.role}</p>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Типографика">
        <div className="space-y-10">
          <div>
            <p className="text-muted font-mono text-xs tracking-widest uppercase">
              Display — Literata (serif) · заголовки
            </p>
            <p className="text-ink font-display mt-3 text-5xl">
              Готовьтесь до собеседования
            </p>
            <p className="text-ink font-display mt-3 text-2xl">
              Три шага от настройки до разбора
            </p>
          </div>
          <div>
            <p className="text-muted font-mono text-xs tracking-widest uppercase">
              Sans — Golos Text · основной текст
            </p>
            <p className="text-ink mt-3 text-lg">
              Тренируйтесь на реалистичных вопросах под вашу профессию и
              уровень.
            </p>
            <p className="text-muted text-body-sm mt-2">
              Мелкий текст карточек и описаний — 0.95rem, утилита text-body-sm.
            </p>
          </div>
          <div>
            <p className="text-muted font-mono text-xs tracking-widest uppercase">
              Mono — JetBrains Mono · данные, лейблы, метрики
            </p>
            <p className="text-ink mt-3 font-mono text-2xl">4.2 / 5</p>
            <p className="text-muted mt-2 font-mono text-xs tracking-[0.2em] uppercase">
              Java-разработчик · Middle
            </p>
          </div>
        </div>
      </Section>

      <Section title="Скругления">
        <div className="flex flex-wrap gap-6">
          {(['sm', 'md', 'lg'] as const).map((r) => (
            <div key={r} className="text-center">
              <div
                className="border-accent bg-paper-2/50 size-20 border-2"
                style={{ borderRadius: `var(--radius-${r})` }}
              />
              <p className="text-muted mt-2 font-mono text-xs">radius-{r}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Кнопки">
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Button>Primary</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="ghost">Ghost</Button>
            <Button disabled>Disabled</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="lg">Primary large</Button>
            <Button size="lg" variant="secondary">
              Secondary large
            </Button>
          </div>
        </div>
      </Section>

      <Section title="Сигнатура — правка рецензента на полях">
        <div className="max-w-md">
          <p className="text-ink">
            HashMap не потокобезопасен, а ConcurrentHashMap разрешает
            конкурентный доступ.
          </p>
          <MarginNote score={4} className="mt-4">
            Верно про сегменты. Уточните, что в Java 8+ это блокировка на уровне
            бакета.
          </MarginNote>
        </div>
      </Section>

      <Section title="Формы и сообщения">
        <div className="max-w-md space-y-5">
          <Field
            label="Email"
            type="email"
            placeholder="you@example.com"
            defaultValue=""
          />
          <Field
            label="Пароль"
            type="password"
            hint="Минимум 8 символов"
            defaultValue=""
          />
          <Alert>Не удалось войти. Проверьте email и пароль.</Alert>
          <Alert tone="success">Пароль обновлён.</Alert>
        </div>
      </Section>

      <Section title="Карточка тарифа">
        <div className="grid max-w-2xl gap-5 sm:grid-cols-2">
          <PlanCard selected onSelect={() => {}} className="p-6">
            <PlanSample name="Про" price="490 ₽" note="в месяц" featured />
          </PlanCard>
          <PlanCard selected={false} onSelect={() => {}} className="p-6">
            <PlanSample name="Бесплатно" price="0 ₽" note="для знакомства" />
          </PlanCard>
        </div>
        <p className="text-muted mt-4 text-sm">
          Выбранная карточка — акцентная рамка, ring и мягкая заливка
          bg-paper-2/50.
        </p>
      </Section>
    </Container>
  )
}

function PlanSample({
  name,
  price,
  note,
  featured,
}: {
  name: string
  price: string
  note: string
  featured?: boolean
}) {
  return (
    <>
      <div className="flex items-baseline justify-between">
        <h3 className="text-ink font-display text-xl">{name}</h3>
        {featured && (
          <span className="bg-accent text-paper rounded-sm px-2 py-0.5 font-mono text-xs tracking-wide">
            Популярный
          </span>
        )}
      </div>
      <p className="mt-4">
        <span className="text-ink font-display text-3xl">{price}</span>
        <span className="text-muted ml-2 text-sm">{note}</span>
      </p>
    </>
  )
}
