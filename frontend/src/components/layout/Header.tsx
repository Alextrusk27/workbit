import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { useAuth } from '@/features/auth/useAuth'
import { cn } from '@/lib/cn'

const links = [
  { label: 'AI-интервью', to: '/#how' },
  { label: 'FAQ', to: '/faq' },
  { label: 'Тарифы', to: '/pricing' },
]

function desktopLinkClass(isActive: boolean): string {
  return cn(
    'text-sm text-ink/75 transition-colors hover:text-ink',
    isActive && 'text-ink underline decoration-accent underline-offset-8',
  )
}

export function Header() {
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const { isAuthenticated } = useAuth()

  // Залогинен — ведём в ЛК, иначе на вход (иначе кнопка гнала на повторный логин).
  const account = isAuthenticated
    ? { to: '/app', label: 'Личный кабинет' }
    : { to: '/login', label: 'Войти' }

  // Закрывать меню при смене роута и по Escape.
  useEffect(() => setOpen(false), [location])
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <header className="border-rule bg-paper/85 sticky top-0 z-40 border-b backdrop-blur">
      <Container>
        <div className="flex h-16 items-center justify-between gap-6">
          <Link to="/" className="rounded-sm" aria-label="workbit — на главную">
            <Logo />
          </Link>

          <nav className="hidden items-center gap-8 md:flex">
            {links.map((l) =>
              l.to.includes('#') ? (
                <Link key={l.to} to={l.to} className={desktopLinkClass(false)}>
                  {l.label}
                </Link>
              ) : (
                <NavLink
                  key={l.to}
                  to={l.to}
                  className={({ isActive }) => desktopLinkClass(isActive)}
                >
                  {l.label}
                </NavLink>
              ),
            )}
          </nav>

          <div className="hidden md:block">
            <Link
              to={account.to}
              className={buttonClasses({ variant: 'secondary' })}
            >
              {account.label}
            </Link>
          </div>

          <button
            type="button"
            className="text-ink -mr-2 inline-flex h-10 w-10 items-center justify-center rounded-md md:hidden"
            aria-label={open ? 'Закрыть меню' : 'Открыть меню'}
            aria-expanded={open}
            aria-controls="mobile-nav"
            onClick={() => setOpen((v) => !v)}
          >
            <span aria-hidden className="relative block h-4 w-5">
              <span
                className={cn(
                  'bg-ink absolute left-0 block h-0.5 w-5 transition-transform duration-200',
                  open ? 'top-1.5 rotate-45' : 'top-0',
                )}
              />
              <span
                className={cn(
                  'bg-ink absolute top-1.5 left-0 block h-0.5 w-5 transition-opacity duration-200',
                  open && 'opacity-0',
                )}
              />
              <span
                className={cn(
                  'bg-ink absolute left-0 block h-0.5 w-5 transition-transform duration-200',
                  open ? 'top-1.5 -rotate-45' : 'top-3',
                )}
              />
            </span>
          </button>
        </div>
      </Container>

      {open && (
        <nav
          id="mobile-nav"
          className="border-rule bg-paper border-t md:hidden"
        >
          <Container>
            <ul className="flex flex-col py-2">
              {links.map((l) => (
                <li key={l.to}>
                  <Link to={l.to} className="text-ink block py-3 text-base">
                    {l.label}
                  </Link>
                </li>
              ))}
              <li className="py-3">
                <Link
                  to={account.to}
                  className={buttonClasses({
                    variant: 'secondary',
                    className: 'w-full',
                  })}
                >
                  {account.label}
                </Link>
              </li>
            </ul>
          </Container>
        </nav>
      )}
    </header>
  )
}
