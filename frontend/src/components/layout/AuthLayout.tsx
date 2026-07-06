import { Link, Outlet } from 'react-router-dom'
import { Logo } from '@/components/ui/Logo'

/** Раскладка для страниц входа/регистрации: центрированная карточка, без
 *  маркетинговой шапки и футера. */
export function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col items-center px-5 py-12 sm:py-20">
      <Link to="/" className="rounded-sm" aria-label="workbit — на главную">
        <Logo />
      </Link>
      <main className="mt-10 w-full max-w-sm sm:mt-14">
        <Outlet />
      </main>
    </div>
  )
}
