import { Link, Outlet } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { ThemeToggle } from '@/components/ui/ThemeToggle'

/** Раскладка для входа/регистрации: центрированная карточка на подсвеченном
 *  фоне, без маркетинговой навигации. */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <header
        className="border-divider border-b backdrop-blur-xl"
        style={{ backgroundColor: 'var(--nav-bg)' }}
      >
        <Container>
          <div className="flex h-17 items-center justify-between gap-6">
            <Link
              to="/"
              className="rounded-sm"
              aria-label="Workbit — на главную"
            >
              <Logo />
            </Link>
            <ThemeToggle />
          </div>
        </Container>
      </header>

      <main className="glow-page relative flex flex-1 items-center justify-center overflow-hidden px-5 py-14">
        <div className="border-line bg-card relative w-full max-w-110 rounded-2xl border p-8 backdrop-blur-[10px] sm:px-8.5 sm:py-9">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
