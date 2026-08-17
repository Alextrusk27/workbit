import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/app/queryClient'
import { router } from '@/app/router'
import './index.css'

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
