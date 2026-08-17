import type { RouteObject } from 'react-router-dom'
import App from '@/App'
import { HomePage } from '@/pages/HomePage'
import { AiInterviewPage } from '@/pages/AiInterviewPage'
import { SkillsTrainerPage } from '@/pages/SkillsTrainerPage'
import { FaqPage } from '@/pages/FaqPage'
import { PricingPage } from '@/pages/PricingPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ErrorPage } from '@/pages/ErrorPage'

export const marketingRoute: RouteObject = {
  path: '/',
  element: <App />,
  errorElement: <ErrorPage />,
  children: [
    { index: true, element: <HomePage /> },
    { path: 'ai-interview', element: <AiInterviewPage /> },
    { path: 'skills-trainer', element: <SkillsTrainerPage /> },
    { path: 'faq', element: <FaqPage /> },
    { path: 'pricing', element: <PricingPage /> },
    {
      path: 'privacy',
      lazy: async () => ({
        Component: (await import('@/pages/PrivacyPage')).PrivacyPage,
      }),
    },
    {
      path: 'user-agreement',
      lazy: async () => ({
        Component: (await import('@/pages/UserAgreementPage'))
          .UserAgreementPage,
      }),
    },
    {
      path: 'offer',
      lazy: async () => ({
        Component: (await import('@/pages/OfferPage')).OfferPage,
      }),
    },
    { path: '*', element: <NotFoundPage /> },
  ],
}
