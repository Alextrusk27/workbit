import type { ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { MarginNote } from '@/components/ui/MarginNote'

const swatches = [
  { name: 'paper', value: '#F7F5F0', className: 'bg-paper' },
  { name: 'paper-2', value: '#EFECE4', className: 'bg-paper-2' },
  { name: 'ink', value: '#1A1A1A', className: 'bg-ink' },
  { name: 'muted', value: '#6E6A5F', className: 'bg-muted' },
  { name: 'rule', value: '#D8D3C7', className: 'bg-rule' },
  { name: 'accent', value: '#234C5E', className: 'bg-accent' },
  { name: 'pine', value: '#1F3D34', className: 'bg-pine' },
  { name: 'edit', value: '#3B6E93', className: 'bg-edit' },
]

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="border-rule border-t py-12">
      <h2 className="text-muted mb-6 font-mono text-xs font-medium tracking-[0.2em] uppercase">
        {title}
      </h2>
      {children}
    </section>
  )
}

export function HomePage() {
  return (
    <Container className="py-16">
      <header className="mb-4 flex items-center justify-between">
        <Logo />
        <span className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Дизайн-система · Фаза 0
        </span>
      </header>

      <p className="font-display text-ink max-w-xl text-2xl leading-snug">
        «Спокойный эксперт» — редакционный премиум. Токены применены, каркас
        готов.
      </p>

      <Section title="Типографика">
        <div className="space-y-4">
          <p className="font-display text-ink text-5xl font-semibold">
            Готовьтесь до собеседования
          </p>
          <p className="text-ink max-w-2xl font-sans text-lg">
            Тренируйтесь на реалистичных вопросах и получайте разбор каждого
            ответа. Golos Text — спокойный гротеск для основного текста.
          </p>
          <p className="text-muted font-mono text-sm">
            JetBrains Mono · данные и метки · score 8/10 · index 03
          </p>
        </div>
      </Section>

      <Section title="Палитра">
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {swatches.map((s) => (
            <div key={s.name} className="border-rule rounded-md border">
              <div className={`h-16 rounded-t-md ${s.className}`} />
              <div className="px-3 py-2">
                <div className="text-ink font-sans text-sm">{s.name}</div>
                <div className="text-muted font-mono text-xs">{s.value}</div>
              </div>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Кнопки">
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">Начать интервью</Button>
          <Button variant="secondary">Посмотреть отчёт</Button>
          <Button variant="ghost">Отмена</Button>
          <Button variant="primary" size="lg">
            Крупная
          </Button>
          <Button variant="primary" disabled>
            Недоступна
          </Button>
        </div>
      </Section>

      <Section title="Сигнатура — правка на полях">
        <div className="grid gap-6 sm:grid-cols-[1fr_auto] sm:items-start">
          <blockquote className="border-rule text-ink max-w-xl border-l-2 pl-4 font-sans">
            HashMap использует хеш-функцию ключа, чтобы разложить пары по
            корзинам. При коллизии элементы складываются в список, а с восьми
            элементов — в сбалансированное дерево.
          </blockquote>
          <MarginNote score={8} className="sm:max-w-[16rem]">
            Точно про treeify. Добавьте, что происходит при resize и почему
            важен hashCode.
          </MarginNote>
        </div>
      </Section>
    </Container>
  )
}
