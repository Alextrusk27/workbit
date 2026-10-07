import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/app/queryClient'
import { router } from '@/app/router'
import './index.css'
import golosCyr from '@fontsource-variable/golos-text/files/golos-text-cyrillic-wght-normal.woff2?url'
import golosLatin from '@fontsource-variable/golos-text/files/golos-text-latin-wght-normal.woff2?url'
import geologicaCyr from '@fontsource-variable/geologica/files/geologica-cyrillic-wght-normal.woff2?url'
import geologicaLatin from '@fontsource-variable/geologica/files/geologica-latin-wght-normal.woff2?url'

for (const href of [golosCyr, golosLatin, geologicaCyr, geologicaLatin]) {
  const l = document.createElement('link')
  l.rel = 'preload'
  l.as = 'font'
  l.type = 'font/woff2'
  l.crossOrigin = 'anonymous'
  l.href = href
  document.head.append(l)
}

const container = document.getElementById('root')!
const app = (
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>
)

if (container.hasChildNodes()) {
  const hydrate = () => hydrateRoot(container, app)
  if (router.state.initialized) {
    hydrate()
  } else {
    const unsubscribe = router.subscribe((state) => {
      if (state.initialized) {
        unsubscribe()
        hydrate()
      }
    })
  }
} else {
  createRoot(container).render(app)
}
