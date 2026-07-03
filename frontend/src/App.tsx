import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'

/** Прокрутка при навигации: к секции по хешу (#how), иначе — наверх.
 *  Уважает prefers-reduced-motion. Зависимость — location целиком (key меняется
 *  при каждой навигации), чтобы повторный клик по тому же хешу снова скроллил. */
function ScrollManager() {
  const location = useLocation()

  useEffect(() => {
    const reduced = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    const behavior = reduced ? 'auto' : 'smooth'
    if (location.hash) {
      const el = document.getElementById(location.hash.slice(1))
      if (el) {
        el.scrollIntoView({ behavior })
        return
      }
    }
    window.scrollTo({ top: 0, behavior })
  }, [location])

  return null
}

function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <ScrollManager />
      <Header />
      <main className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

export default App
