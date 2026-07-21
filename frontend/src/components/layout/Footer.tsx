import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'

const columns = [
  {
    title: 'Продукт',
    links: [
      { label: 'AI-интервью', to: '/#how' },
      { label: 'Тарифы', to: '/pricing' },
      { label: 'FAQ', to: '/faq' },
    ],
  },
  {
    title: 'Аккаунт',
    links: [{ label: 'Войти', to: '/login' }],
  },
  {
    title: 'Документы',
    links: [
      { label: 'Конфиденциальность', to: '/privacy' },
      { label: 'Соглашение', to: '/user-agreement' },
      { label: 'Оферта', to: '/offer' },
    ],
  },
]

export function Footer() {
  return (
    <footer className="border-rule mt-24 border-t">
      <Container className="pt-11 pb-5">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.5fr_1fr_1fr_1fr]">
          <div className="max-w-xs">
            <Logo />
            <p className="text-muted mt-3 text-sm">
              Тренажёр собеседований с разбором каждого ответа. Готовьтесь до
              собеседования, а не на нём.
            </p>
          </div>

          {columns.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <h2 className="text-muted mb-3 font-mono text-xs tracking-[0.2em] uppercase">
                {col.title}
              </h2>
              <ul className="space-y-2">
                {col.links.map((l) => (
                  <li key={l.to}>
                    <Link
                      to={l.to}
                      className="text-ink/80 hover:text-ink text-sm transition-colors"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="text-muted mt-8 flex flex-wrap items-center gap-x-4 gap-y-2 font-mono text-xs">
          <span>© {new Date().getFullYear()} workbit. Все права защищены.</span>
          {import.meta.env.DEV && (
            <Link
              to="/brand"
              className="text-accent hover:text-accent-hover underline underline-offset-2 transition-colors"
            >
              Брендбук
            </Link>
          )}
        </div>
      </Container>
    </footer>
  )
}
