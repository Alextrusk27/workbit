import { Link } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'

const columns = [
  {
    title: 'Продукт',
    links: [
      { label: 'AI-интервью', to: '/ai-interview' },
      { label: 'Тренажёр навыков', to: '/skills-trainer' },
    ],
  },
  {
    title: 'Помощь',
    links: [
      { label: 'Тарифы', to: '/pricing' },
      { label: 'FAQ', to: '/faq' },
    ],
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
    <footer className="border-divider mt-8 border-t sm:mt-12">
      <Container className="pt-7 pb-5 sm:pb-6">
        <div className="grid grid-cols-2 gap-x-6 gap-y-6 sm:grid-cols-3 lg:grid-cols-[minmax(0,1fr)_auto_auto_auto] lg:gap-x-16">
          <div className="col-span-2 flex max-w-[36ch] flex-col gap-2.5 sm:col-span-3 lg:col-span-1">
            <Logo />
            <p className="text-muted text-[13px]">
              Тренажёр собеседований с AI-рецензентом под профессию или навык
            </p>
            <div className="text-dim flex flex-wrap items-center gap-x-4 gap-y-2 text-[12.5px]">
              <span>
                © {new Date(__BUILD_TS__).getFullYear()} Workbit. Все права
                защищены.
              </span>
              {import.meta.env.DEV && (
                <Link
                  to="/brand"
                  className="text-indigo hover:text-violet underline underline-offset-2 transition-colors"
                >
                  Брендбук
                </Link>
              )}
            </div>
          </div>

          {columns.map((col) => (
            <nav key={col.title} aria-label={col.title}>
              <p className="text-dim mb-2.5 text-[11px] font-semibold tracking-[0.07em] uppercase">
                {col.title}
              </p>
              <ul className="flex flex-col gap-[7px]">
                {col.links.map((l) => (
                  <li key={l.to}>
                    <Link
                      to={l.to}
                      className="text-muted hover:text-ink text-[13.5px] transition-colors"
                    >
                      {l.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </nav>
          ))}
        </div>
      </Container>
    </footer>
  )
}
