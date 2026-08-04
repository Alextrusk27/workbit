import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { ThemeToggle } from '@/components/ui/ThemeToggle'
import { UserMenu } from '@/components/layout/UserMenu'
import { useAuth } from '@/features/auth/useAuth'
import { motionTokens } from '@/lib/motion'
import { cn } from '@/lib/cn'

const links = [
  { label: 'AI-интервью', to: '/ai-interview' },
  { label: 'Тренажёр навыков', to: '/skills-trainer' },
  { label: 'Тарифы', to: '/pricing' },
]

function navLinkClass(isActive: boolean): string {
  return cn(
    'text-muted hover:text-ink text-[14.5px] font-medium whitespace-nowrap transition-colors',
    isActive && 'text-ink',
  )
}

export function Header() {
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const { isAuthenticated } = useAuth()
  const reduce = useReducedMotion()

  useEffect(() => setOpen(false), [location])
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <header
      className="border-divider sticky top-0 z-50 border-b backdrop-blur-xl"
      style={{ backgroundColor: 'var(--nav-bg)' }}
    >
      <Container>
        <div className="relative flex h-17 items-center gap-8">
          <Link to="/" className="rounded-sm" aria-label="Workbit — на главную">
            <Logo />
          </Link>

          <nav
            aria-label="Основная навигация"
            className="absolute left-1/2 hidden -translate-x-1/2 items-center gap-7 lg:flex"
          >
            {links.map((l) => (
              <NavLink
                key={l.to}
                to={l.to}
                className={({ isActive }) => navLinkClass(isActive)}
              >
                {l.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-2 sm:gap-3">
            <ThemeToggle />
            {isAuthenticated ? (
              <UserMenu />
            ) : (
              <Link to="/login" className={buttonClasses({ size: 'sm' })}>
                Войти
              </Link>
            )}
            <button
              type="button"
              className="text-ink -mr-2 inline-flex size-10 touch-manipulation items-center justify-center rounded-md lg:hidden"
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
        </div>
      </Container>

      <AnimatePresence>
        {open && (
          <motion.nav
            key="mobile-nav"
            id="mobile-nav"
            aria-label="Мобильная навигация"
            className="border-divider bg-canvas overflow-hidden border-t lg:hidden"
            initial={
              reduce
                ? { opacity: 0 }
                : { opacity: 0, y: -motionTokens.distance.sm }
            }
            animate={{ opacity: 1, y: 0 }}
            exit={
              reduce
                ? { opacity: 0 }
                : { opacity: 0, y: -motionTokens.distance.sm }
            }
            transition={{
              duration: motionTokens.duration.fast,
              ease: motionTokens.easing.sharp,
            }}
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
                <li>
                  <Link to="/faq" className="text-ink block py-3 text-base">
                    FAQ
                  </Link>
                </li>
                <li className="py-3">
                  <Link
                    to={isAuthenticated ? '/app' : '/login'}
                    className={buttonClasses({
                      variant: 'secondary',
                      className: 'w-full',
                    })}
                  >
                    {isAuthenticated ? 'Личный кабинет' : 'Войти'}
                  </Link>
                </li>
              </ul>
            </Container>
          </motion.nav>
        )}
      </AnimatePresence>
    </header>
  )
}
