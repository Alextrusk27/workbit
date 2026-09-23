import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { AnimatePresence, motion, useReducedMotion } from 'motion/react'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { Skeleton } from '@/components/ui/Skeleton'
import { ThemeSwitch } from '@/components/ui/ThemeSwitch'
import { ThemeToggle } from '@/components/ui/ThemeToggle'
import { HeaderBalance } from '@/components/layout/HeaderBalance'
import { UserMenu } from '@/components/layout/UserMenu'
import { useAuth } from '@/features/auth/useAuth'
import { useTopUpModal } from '@/features/billing/useTopUpModal'
import { motionTokens, springs } from '@/lib/motion'
import { cn } from '@/lib/cn'

const links = [
  { label: 'AI-интервью', to: '/ai-interview' },
  { label: 'Тренажёр навыков', to: '/skills-trainer' },
  { label: 'FAQ', to: '/faq' },
  { label: 'Блог', to: '/blog' },
]

const mobileRowClass =
  'text-ink hover:bg-glass flex min-h-11 w-full items-center rounded-lg px-3 text-left text-base transition-colors'

function navLinkClass(isActive = false): string {
  return cn(
    'text-muted hover:text-ink text-[14.5px] font-medium whitespace-nowrap transition-colors',
    isActive && 'text-ink',
  )
}

export function Header() {
  const [menu, setMenu] = useState<'nav' | 'user' | null>(null)
  const open = menu === 'nav'
  const headerRef = useRef<HTMLElement>(null)
  const location = useLocation()
  const { isAuthenticated, isLoading } = useAuth()
  const reduce = useReducedMotion()
  const openTopUp = useTopUpModal()
  const toggleMenu = (m: 'nav' | 'user') =>
    setMenu((cur) => (cur === m ? null : m))

  useEffect(() => setMenu(null), [location])
  useEffect(() => {
    if (!menu) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMenu(null)
    const onDown = (e: MouseEvent) => {
      if (!headerRef.current?.contains(e.target as Node)) setMenu(null)
    }
    window.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onDown)
    return () => {
      window.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onDown)
    }
  }, [menu])

  return (
    <header
      ref={headerRef}
      className="border-divider sticky top-0 z-50 border-b backdrop-blur-xl"
      style={{ backgroundColor: 'var(--nav-bg)' }}
    >
      <Container>
        <div className="relative flex h-17 items-center gap-4 sm:gap-8">
          <Link
            to="/"
            className="shrink-0 rounded-sm"
            aria-label="Workbit — на главную"
          >
            <Logo className="max-sm:text-[22px]" />
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
            {!isAuthenticated && (
              <button
                type="button"
                onClick={() => openTopUp()}
                className={navLinkClass()}
              >
                Цены
              </button>
            )}
          </nav>

          <div className="ml-auto flex min-w-0 items-center gap-2 sm:gap-3">
            <ThemeToggle className="shrink-0 max-sm:hidden" />
            {isLoading ? (
              <Skeleton className="h-7 w-[110px] rounded-md" />
            ) : isAuthenticated ? (
              <>
                <HeaderBalance />
                <UserMenu
                  open={menu === 'user'}
                  onToggle={() => toggleMenu('user')}
                  onClose={() => setMenu(null)}
                />
              </>
            ) : (
              <Link to="/login" className={buttonClasses({ size: 'sm' })}>
                Войти
              </Link>
            )}
            <button
              type="button"
              className="text-ink -mr-2 inline-flex size-11 shrink-0 touch-manipulation items-center justify-center rounded-md lg:hidden"
              aria-label={open ? 'Закрыть меню' : 'Открыть меню'}
              aria-expanded={open}
              aria-controls="mobile-nav"
              onClick={() => toggleMenu('nav')}
            >
              <span aria-hidden className="relative block h-[18px] w-6">
                <span
                  className={cn(
                    'bg-ink absolute left-0 block h-0.5 w-6 transition-transform duration-200',
                    open ? 'top-2 rotate-45' : 'top-0',
                  )}
                />
                <span
                  className={cn(
                    'bg-ink absolute top-2 left-0 block h-0.5 w-6 transition-opacity duration-200',
                    open && 'opacity-0',
                  )}
                />
                <span
                  className={cn(
                    'bg-ink absolute left-0 block h-0.5 w-6 transition-transform duration-200',
                    open ? 'top-2 -rotate-45' : 'top-4',
                  )}
                />
              </span>
            </button>
          </div>
          <AnimatePresence>
            {open && (
              <motion.nav
                key="mobile-nav"
                id="mobile-nav"
                aria-label="Мобильная навигация"
                className="border-line bg-pop shadow-pop absolute inset-x-0 top-full z-50 mt-2 rounded-2xl border p-2 lg:hidden"
                initial={
                  reduce
                    ? { opacity: 0 }
                    : { opacity: 0, y: -motionTokens.distance.xs }
                }
                animate={{ opacity: 1, y: 0 }}
                exit={
                  reduce
                    ? { opacity: 0 }
                    : { opacity: 0, y: -motionTokens.distance.xs }
                }
                transition={springs.instant}
              >
                <ul className="flex flex-col">
                  {links.map((l) => (
                    <li key={l.to}>
                      <Link to={l.to} className={mobileRowClass}>
                        {l.label}
                      </Link>
                    </li>
                  ))}
                  {!isAuthenticated && (
                    <>
                      <li>
                        <button
                          type="button"
                          onClick={() => {
                            setMenu(null)
                            openTopUp()
                          }}
                          className={mobileRowClass}
                        >
                          Цены
                        </button>
                      </li>
                      <li className="px-1 pt-2 sm:hidden">
                        <ThemeSwitch />
                      </li>
                      <li className="px-1 pt-2 pb-1">
                        {isLoading ? (
                          <Skeleton className="h-11 w-full rounded-lg" />
                        ) : (
                          <Link
                            to="/login"
                            className={buttonClasses({
                              variant: 'secondary',
                              className: 'w-full',
                            })}
                          >
                            Войти
                          </Link>
                        )}
                      </li>
                    </>
                  )}
                </ul>
              </motion.nav>
            )}
          </AnimatePresence>
        </div>
      </Container>
    </header>
  )
}
