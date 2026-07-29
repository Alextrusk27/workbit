import type { ReactNode } from 'react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Chip } from '@/components/ui/Chip'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Field } from '@/components/ui/Field'
import { MarginNote } from '@/components/ui/MarginNote'
import { PlanCard } from '@/components/ui/PlanCard'
import { Stars } from '@/components/ui/Stars'
import { ThemeToggle } from '@/components/ui/ThemeToggle'
import { plans } from '@/content/plans'
import { usePageTitle } from '@/lib/usePageTitle'
import { useTheme, type Theme } from '@/lib/useTheme'

interface Token {
  name: string
  role: string
  /** Тёмный образец — светлый текст поверх; иначе тёмный. */
  dark?: boolean
}

const PALETTE: Token[] = [
  { name: 'canvas', role: 'Фон страницы' },
  { name: 'canvas-2', role: 'Подложка панелей' },
  { name: 'card', role: 'Карточки, стеклянные поверхности' },
  { name: 'surface', role: 'Поля ввода, пузыри рецензента' },
  { name: 'line', role: 'Границы карточек' },
  { name: 'divider', role: 'Разделители' },
  { name: 'ink', role: 'Основной текст', dark: true },
  { name: 'muted', role: 'Вторичный текст', dark: true },
  { name: 'dim', role: 'Подписи и мелкие пояснения', dark: true },
  { name: 'indigo', role: 'Основной акцент', dark: true },
  { name: 'violet', role: 'Второй цвет градиента', dark: true },
  { name: 'cyan', role: 'Замыкающий цвет градиента', dark: true },
  { name: 'star', role: 'Звёзды оценки' },
  { name: 'ok', role: 'Успех, завершённые сессии', dark: true },
  { name: 'danger', role: 'Ошибки и удаление', dark: true },
]

const TOKEN_NAMES = PALETTE.map((t) => t.name)

/** Читает вычисленные значения CSS-переменных — токены остаются источником
 *  истины; перечитывает при смене темы. */
function useTokenHex(names: string[], theme: Theme): Record<string, string> {
  const [hex, setHex] = useState<Record<string, string>>({})
  useEffect(() => {
    const styles = getComputedStyle(document.documentElement)
    const next: Record<string, string> = {}
    for (const n of names) {
      next[n] = styles.getPropertyValue(`--color-${n}`).trim().toUpperCase()
    }
    setHex(next)
  }, [names, theme])
  return hex
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-divider border-t py-14">
      <h2 className="text-ink text-2xl">{title}</h2>
      <div className="mt-8">{children}</div>
    </section>
  )
}

export function BrandPage() {
  usePageTitle('Брендбук')
  const { theme } = useTheme()
  const hex = useTokenHex(TOKEN_NAMES, theme)

  return (
    <Container className="py-12 sm:py-16">
      <div className="flex items-center justify-between">
        <Link
          to="/"
          className="text-indigo hover:text-violet text-sm transition-colors"
        >
          ← На главную
        </Link>
        <ThemeToggle />
      </div>

      <header className="mt-8">
        <Eyebrow>Брендбук</Eyebrow>
        <h1 className="text-ink mt-4 text-[clamp(32px,4.5vw,48px)] font-extrabold tracking-[-0.03em]">
          Тёмный <span className="text-grad">AI-SaaS</span>
        </h1>
        <p className="text-muted mt-5 max-w-xl text-lg">
          Живой справочник дизайн-системы: палитра, типографика и компоненты.
          Значения читаются из токенов в{' '}
          <code className="text-sm">src/index.css</code> — источника истины.
        </p>
      </header>

      <Section title="Палитра">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {PALETTE.map((t) => (
            <div
              key={t.name}
              className="border-line overflow-hidden rounded-xl border"
            >
              <div
                className="flex h-24 items-end p-3"
                style={{ backgroundColor: `var(--color-${t.name})` }}
              >
                <span
                  className={`text-xs ${t.dark ? 'text-white' : 'text-ink'}`}
                >
                  {hex[t.name] ?? '…'}
                </span>
              </div>
              <div className="bg-card p-3">
                <p className="text-ink text-sm">--color-{t.name}</p>
                <p className="text-dim mt-1 text-xs">{t.role}</p>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Градиенты">
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="border-line overflow-hidden rounded-xl border">
            <div className="bg-grad h-24" />
            <div className="bg-card p-3">
              <p className="text-ink text-sm">--grad-btn</p>
              <p className="text-dim mt-1 text-xs">
                Первичные кнопки, аватар рецензента, пузырь пользователя
              </p>
            </div>
          </div>
          <div className="border-line overflow-hidden rounded-xl border">
            <div
              className="h-24"
              style={{ backgroundImage: 'var(--grad-brand)' }}
            />
            <div className="bg-card p-3">
              <p className="text-ink text-sm">--grad-brand</p>
              <p className="text-dim mt-1 text-xs">
                Логотип и акцентные слова заголовков (утилита text-grad)
              </p>
            </div>
          </div>
        </div>
      </Section>

      <Section title="Типографика">
        <div className="space-y-10">
          <div>
            <Eyebrow>Inter · заголовки</Eyebrow>
            <p className="text-ink mt-3 text-[clamp(38px,5vw,58px)] leading-[1.08] font-extrabold tracking-[-0.03em]">
              Тренажёр <span className="text-grad">собеседований</span>
            </p>
            <p className="text-ink mt-3 text-[clamp(28px,3.6vw,40px)]">
              Как проходит сессия
            </p>
          </div>
          <div>
            <Eyebrow>Inter · текст</Eyebrow>
            <p className="text-ink mt-3 text-lg">
              Реалистичные вопросы под вашу профессию и уровень, разбор каждого
              ответа и вероятность оффера.
            </p>
            <p className="text-muted mt-2 text-[14.5px]">
              Мелкий текст карточек и описаний — 14.5px, цвет muted.
            </p>
            <p className="text-dim mt-2 text-[13px]">
              Подписи и пояснения — 13px, цвет dim.
            </p>
          </div>
          <div>
            <Eyebrow>Числа</Eyebrow>
            <p className="text-ink mt-3 text-[34px] font-extrabold tracking-[-0.02em] tabular-nums">
              4,2{' '}
              <span className="text-muted text-[17px] font-medium">/ 5</span>
            </p>
          </div>
        </div>
      </Section>

      <Section title="Скругления">
        <div className="flex flex-wrap gap-6">
          {(['sm', 'md', 'lg', 'xl', '2xl', '3xl'] as const).map((r) => (
            <div key={r} className="text-center">
              <div
                className="border-indigo bg-card size-20 border-2"
                style={{ borderRadius: `var(--radius-${r})` }}
              />
              <p className="text-dim mt-2 text-xs">radius-{r}</p>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Кнопки и чипы">
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Button>Primary</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="danger">Danger</Button>
            <Button disabled>Disabled</Button>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <Button size="sm">Small</Button>
            <Button size="lg">Large</Button>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <Chip selected count={12}>
              Все
            </Chip>
            <Chip count={4}>В процессе</Chip>
            <Chip count={8}>Завершено</Chip>
          </div>
        </div>
      </Section>

      <Section title="Сигнатура — правка рецензента на полях">
        <div className="max-w-md">
          <p className="text-muted">
            HashMap не потокобезопасен, а ConcurrentHashMap разрешает
            конкурентный доступ.
          </p>
          <MarginNote score={4} className="mt-4">
            Верно про сегменты. Уточните, что в Java 8+ это блокировка на уровне
            бакета.
          </MarginNote>
        </div>
      </Section>

      <Section title="Диалог">
        <div className="flex max-w-lg flex-col gap-3.5">
          <ChatBubble role="bot" who="Вопрос 3 / 10">
            Чем отличается HashMap от ConcurrentHashMap?
          </ChatBubble>
          <ChatBubble role="user" who="Вы">
            HashMap не потокобезопасен, а ConcurrentHashMap разрешает
            конкурентный доступ…
          </ChatBubble>
          <ChatBubble
            role="bot"
            who="Уточняющий вопрос"
            quote={{
              name: 'Вы',
              text: 'HashMap не потокобезопасен, а ConcurrentHashMap разрешает конкурентный доступ…',
            }}
          >
            А что происходит при ресайзе под конкурентной записью?
          </ChatBubble>
        </div>
      </Section>

      <Section title="Оценка">
        <div className="flex flex-wrap items-center gap-6">
          <Stars value={4} className="text-2xl" />
          <Stars value={3.5} className="text-2xl" />
          <Stars value={5} className="text-2xl" />
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
        <div className="grid max-w-3xl gap-5 sm:grid-cols-2">
          {plans.slice(0, 2).map((p) => (
            <PlanCard
              key={p.name}
              plan={p}
              features={p.previewFeatures}
              to="/pricing"
            />
          ))}
        </div>
      </Section>
    </Container>
  )
}
