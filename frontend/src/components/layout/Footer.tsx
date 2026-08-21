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
    <footer className="border-divider mt-8 border-t sm:mt-12">
      <Container className="pt-7 pb-5 sm:pt-9 sm:pb-6">
        <div className="grid grid-cols-2 gap-6 sm:gap-8 lg:grid-cols-[2fr_1fr_1fr]">
          <div className="col-span-2 max-w-[34ch] lg:col-span-1">
            <Logo className="text-[26px] sm:text-[40px]" />
            <p className="text-dim mt-2.5 text-[13px] sm:mt-3 sm:text-sm">
              Тренажёр собеседований с AI-рецензентом: вопросы под роль, разбор
              ответов, вероятность оффера.
            </p>
          </div>

          {columns.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <p className="text-dim mb-2.5 text-xs font-semibold tracking-[0.06em] uppercase sm:mb-3.5 sm:text-[13px]">
                {col.title}
              </p>
              <ul className="flex flex-col gap-1.5 sm:gap-[7px]">
                {col.links.map((l) => (
                  <li key={l.to}>
                    <Link
                      to={l.to}
                      className="text-muted hover:text-ink text-[13.5px] transition-colors sm:text-sm"
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
