import { useState } from 'react'
import { Link } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { PlanCard } from '@/components/ui/PlanCard'
import { plans } from '@/content/plans'
import { usePageTitle } from '@/lib/usePageTitle'

function Dash() {
  return (
    <span className="text-accent shrink-0" aria-hidden>
      —
    </span>
  )
}

export function PricingPage() {
  usePageTitle('Тарифы')
  const [selected, setSelected] = useState(
    () => (plans.find((p) => p.featured) ?? plans[0]).name,
  )
  return (
    <Container className="py-16 sm:py-24">
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Тарифы
      </p>
      <h1 className="text-ink mt-4 max-w-2xl text-4xl sm:text-5xl">
        Один тренажёр, два режима
      </h1>
      <p className="text-muted mt-5 max-w-xl text-lg">
        Начните бесплатно, чтобы понять формат. Переходите на Про, когда
        готовитесь всерьёз и нужен глубокий разбор.
      </p>

      <div
        role="radiogroup"
        aria-label="Выбор тарифа"
        className="mt-12 grid items-stretch gap-5 sm:grid-cols-2"
      >
        {plans.map((p) => {
          const isSelected = selected === p.name
          return (
            <PlanCard
              key={p.name}
              selected={isSelected}
              onSelect={() => setSelected(p.name)}
              className="p-7 sm:p-9"
            >
              <div className="flex items-baseline justify-between">
                <h2 className="text-ink font-display text-2xl">{p.name}</h2>
                {p.featured && (
                  <span className="bg-accent text-paper rounded-sm px-2 py-0.5 font-mono text-xs tracking-wide">
                    Популярный
                  </span>
                )}
              </div>
              <p className="mt-5">
                <span className="text-ink font-display text-4xl tabular-nums">
                  {p.price}
                </span>
                <span className="text-muted ml-2 text-sm">{p.note}</span>
              </p>
              <ul className="mt-7 grow space-y-3">
                {p.features.map((f) => (
                  <li key={f} className="text-ink/85 flex gap-3">
                    <Dash />
                    {f}
                  </li>
                ))}
              </ul>
              <Link
                to="/login"
                onClick={(e) => e.stopPropagation()}
                className={buttonClasses({
                  variant: isSelected ? 'primary' : 'secondary',
                  size: 'lg',
                  className: 'mt-8 w-full',
                })}
              >
                {p.cta}
              </Link>
            </PlanCard>
          )
        })}
      </div>

      <p className="text-muted mt-8 text-sm">
        Цены указаны для примера и могут измениться до запуска.
      </p>
    </Container>
  )
}
