import { useEffect } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import { Header } from '@/components/layout/Header'
import { Footer } from '@/components/layout/Footer'
import { seoFor } from '@/content/seo'

function HeadMeta() {
  const { pathname } = useLocation()

  useEffect(() => {
    const { title, description, canonical } = seoFor(pathname)
    document.title = title
    document
      .querySelector('meta[name="description"]')
      ?.setAttribute('content', description)
    let link = document.querySelector('link[rel="canonical"]')
    if (canonical) {
      if (!link) {
        link = document.createElement('link')
        link.setAttribute('rel', 'canonical')
        document.head.append(link)
      }
      link.setAttribute('href', canonical)
    } else {
      link?.remove()
    }
  }, [pathname])

  return null
}

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
      <HeadMeta />
      <ScrollManager />
      <a
        href="#main"
        className="focus:bg-canvas focus:text-ink sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-60 focus:rounded-md focus:px-4 focus:py-2 focus:shadow-lg"
      >
        Перейти к содержимому
      </a>
      <Header />
      <main id="main" tabIndex={-1} className="flex-1">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

export default App
