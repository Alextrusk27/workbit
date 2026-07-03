import { Link, Outlet, useNavigate } from 'react-router-dom'
import { Container } from '@/components/ui/Container'
import { Logo } from '@/components/ui/Logo'
import { useAuth, useLogout } from '@/features/auth/useAuth'

/** Раскладка личного кабинета: своя тонкая шапка (лого, email, выход), без
 *  маркетинговой навигации. */
export function AppLayout() {
  const { user } = useAuth()
  const logout = useLogout()
  const navigate = useNavigate()

  const onLogout = () => {
    logout.mutate(undefined, {
      onSettled: () => navigate('/login', { replace: true }),
    })
  }

  return (
    <div className="flex min-h-screen flex-col">
      <header className="border-rule bg-paper/85 sticky top-0 z-40 border-b backdrop-blur">
        <Container>
          <div className="flex h-16 items-center justify-between gap-4">
            <Link
              to="/"
              className="rounded-sm"
              aria-label="workbit — на главную"
            >
              <Logo />
            </Link>
            <div className="flex items-center gap-4">
              {user && (
                <span className="text-muted hidden text-sm sm:inline">
                  {user.email}
                </span>
              )}
              <button
                type="button"
                onClick={onLogout}
                disabled={logout.isPending}
                className="text-ink/75 hover:text-ink text-sm transition-colors disabled:opacity-50"
              >
                Выйти
              </button>
            </div>
          </div>
        </Container>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}
