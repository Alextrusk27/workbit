import { Link, Outlet } from 'react-router-dom'
import { Logo } from '@/components/ui/Logo'

const BENEFITS = [
  'Реалистичные ИИ-интервью по твоей вакансии',
  'Тренажёр навыков с обратной связью',
  'Подробный отчёт после каждой сессии',
]

/** Раскладка для входа: слева тёмная брендовая панель (на мобильном — тёмная
 *  шапка), справа форма на светлом фоне. Тема всегда светлая. */
export function AuthLayout() {
  return (
    <div className="theme-light bg-canvas flex min-h-screen flex-col lg:flex-row">
      <aside className="glow-auth dark bg-canvas relative overflow-hidden px-6 pt-5.5 pb-6 lg:flex lg:w-[43%] lg:min-w-130 lg:flex-col lg:justify-between lg:px-14 lg:py-12">
        <div className="relative">
          <Link to="/" className="rounded-sm" aria-label="Workbit — на главную">
            <Logo className="text-[24px] lg:text-[28px]" />
          </Link>
          <p className="text-muted mt-2.5 text-[14px] lg:hidden">
            Подготовка к собеседованиям с ИИ
          </p>
        </div>

        <div className="relative hidden max-w-120 lg:block">
          <h2 className="text-ink text-[38px]">
            Подготовка к собеседованиям с ИИ
          </h2>
          <p className="text-muted mt-4 text-base leading-relaxed">
            Репетируй интервью по своей вакансии и получай разбор ответов после
            каждой сессии.
          </p>
          <ul className="mt-8 flex flex-col gap-4">
            {BENEFITS.map((benefit) => (
              <li key={benefit} className="flex items-start gap-3">
                <span className="border-indigo/45 text-indigo mt-px flex size-6 shrink-0 items-center justify-center rounded-full border">
                  <svg
                    viewBox="0 0 24 24"
                    className="size-3"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="M20 6 9 17l-5-5" />
                  </svg>
                </span>
                <span className="text-ink/80 text-[15.5px]">{benefit}</span>
              </li>
            ))}
          </ul>
        </div>

        <p className="text-dim relative hidden text-[13px] lg:block">
          © 2026 Workbit
        </p>
      </aside>

      <main className="flex flex-1 justify-center px-6 pt-11 pb-10 lg:items-center lg:px-10 lg:py-14">
        <div className="w-full max-w-110">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
