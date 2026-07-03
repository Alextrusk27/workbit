import { createBrowserRouter } from 'react-router-dom'
import App from '@/App'
import { HomePage } from '@/pages/HomePage'
import { FaqPage } from '@/pages/FaqPage'
import { PricingPage } from '@/pages/PricingPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ErrorPage } from '@/pages/ErrorPage'

export const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    errorElement: <ErrorPage />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'faq', element: <FaqPage /> },
      { path: 'pricing', element: <PricingPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
