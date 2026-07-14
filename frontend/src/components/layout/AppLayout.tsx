import { useEffect, useRef, useState } from 'react'
import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { ThemeToggle } from '@/components/ui/ThemeToggle'
import { Footer } from '@/components/layout/Footer'
import { useAuth, useLogout } from '@/features/auth/useAuth'

export function AppLayout() {
  const { user } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()

  const onLogout = () => {
    navigate('/', { replace: true })
    logout.mutate()
  }

  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="focus:bg-paper focus:text-ink sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:rounded-md focus:px-4 focus:py-2 focus:shadow-lg"
      >
        Перейти к содержимому
      </a>
      <header className="border-rule bg-paper/85 sticky top-0 z-40 border-b backdrop-blur">
        <Container>
          <div className="flex h-16 items-center justify-between gap-6">
            <Link
              to="/"
              className="rounded-sm"
              aria-label="workbit — на главную"
            >
              <Logo />
            </Link>
            <nav
              aria-label="Основная навигация"
              className="hidden items-center gap-8 md:flex"
            >
              <Link
                to="/#how"
                className="text-ink/75 hover:text-ink text-sm transition-colors"
              >
                AI-интервью
              </Link>
              <Link
                to="/faq"
                className="text-ink/75 hover:text-ink text-sm transition-colors"
              >
                FAQ
              </Link>
              <Link
                to="/pricing"
                className="text-ink/75 hover:text-ink text-sm transition-colors"
              >
                Тарифы
              </Link>
            </nav>
            <div className="flex items-center gap-2 sm:gap-4">
              <ThemeToggle />
              {user && (
                <UserMenu
                  email={user.email}
                  onLogout={onLogout}
                  loggingOut={logout.isPending}
                />
              )}
            </div>
          </div>
        </Container>
      </header>
      <main id="main" tabIndex={-1} className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

function UserMenu({
  email,
  onLogout,
  loggingOut,
}: {
  email: string
  onLogout: () => void
  loggingOut: boolean
}) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        className="text-ink/75 hover:text-ink flex max-w-[50vw] touch-manipulation items-center gap-1.5 text-sm transition-colors sm:max-w-none"
      >
        <span className="min-w-0 truncate">{email}</span>
        <svg
          viewBox="0 0 12 12"
          className={`size-3 shrink-0 transition-transform ${open ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          strokeWidth="1.5"
          aria-hidden="true"
        >
          <path d="M2.5 4.5 6 8l3.5-3.5" strokeLinecap="round" />
        </svg>
      </button>

      {open && (
        <div className="border-rule bg-paper absolute right-0 z-50 mt-2 w-48 rounded-md border py-1 shadow-lg">
          <Link
            to="/app"
            onClick={() => setOpen(false)}
            className="text-ink hover:bg-paper-2 block px-3 py-2 text-sm transition-colors"
          >
            Личный кабинет
          </Link>
          <Link
            to="/app/settings"
            onClick={() => setOpen(false)}
            className="text-ink hover:bg-paper-2 block px-3 py-2 text-sm transition-colors"
          >
            Настройки
          </Link>
          <button
            type="button"
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
            disabled={loggingOut}
            className="text-ink hover:bg-paper-2 block w-full px-3 py-2 text-left text-sm transition-colors disabled:opacity-50"
          >
            {loggingOut ? 'Выходим…' : 'Выйти'}
          </button>
        </div>
      )}
    </div>
  )
}
