import { Outlet } from 'react-router-dom'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'

export function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="focus:bg-canvas focus:text-ink sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-60 focus:rounded-md focus:px-4 focus:py-2 focus:shadow-lg"
      >
        Перейти к содержимому
      </a>
      <Header />
      <main
        id="main"
        tabIndex={-1}
        className="flex-1 pt-10 pb-10 sm:pt-24 sm:pb-16"
      >
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}
