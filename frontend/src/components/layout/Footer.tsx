import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'

const columns = [
  {
    title: 'Продукт',
    links: [
      { label: 'AI-интервью', to: '/ai-interview' },
      { label: 'Тренажёр навыков', to: '/skills-trainer' },
      { label: 'Тарифы', to: '/pricing' },
      { label: 'FAQ', to: '/faq' },
    ],
  },
  {
    title: 'Правовое',
    links: [
      { label: 'Конфиденциальность', to: '/privacy' },
      { label: 'Соглашение', to: '/user-agreement' },
      { label: 'Оферта', to: '/offer' },
    ],
  },
]

export function Footer() {
  return (
    <footer className="border-divider mt-20 border-t">
      <Container className="pt-9 pb-6">
        <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr]">
          <div className="max-w-[34ch]">
            <Logo className="text-[40px]" />
            <p className="text-dim mt-3 text-sm">
              Тренажёр собеседований с AI-рецензентом: вопросы под роль, разбор
              ответов, вероятность оффера.
            </p>
          </div>

          {columns.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <h2 className="text-dim mb-3.5 text-[13px] font-semibold tracking-[0.06em] uppercase">
                {col.title}
              </h2>
              <ul className="flex flex-col gap-[7px]">
                {col.links.map((l) => (
                  <li key={l.to}>
                    <Link
                      to={l.to}
                      className="text-muted hover:text-ink text-sm transition-colors"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>

        <div className="border-divider text-dim mt-6 flex flex-wrap items-center gap-x-4 gap-y-2 border-t pt-4.5 text-[13px]">
          <span>© {new Date().getFullYear()} Workbit. Все права защищены.</span>
          {import.meta.env.DEV && (
            <Link
              to="/brand"
              className="text-indigo hover:text-violet underline underline-offset-2 transition-colors"
            >
              Брендбук
            </Link>
          )}
        </div>
      </Container>
    </footer>
  )
}
