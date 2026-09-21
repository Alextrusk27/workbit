import type { RouteObject } from 'react-router-dom'
import App from '@/App'
import { HomePage } from '@/pages/HomePage'
import { AiInterviewPage } from '@/pages/AiInterviewPage'
import { SkillsTrainerPage } from '@/pages/SkillsTrainerPage'
import { FaqPage } from '@/pages/FaqPage'
import { BlogPage } from '@/pages/BlogPage'
import { ArticlePage } from '@/pages/ArticlePage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ErrorPage } from '@/pages/ErrorPage'
import { findArticle } from '@/content/articles'

export const marketingRoute: RouteObject = {
  path: '/',
  element: <App />,
  errorElement: <ErrorPage />,
  children: [
    { index: true, element: <HomePage /> },
    { path: 'ai-interview', element: <AiInterviewPage /> },
    { path: 'skills-trainer', element: <SkillsTrainerPage /> },
    { path: 'faq', element: <FaqPage /> },
    { path: 'blog', element: <BlogPage /> },
    {
      path: 'blog/:rubric/:slug',
      loader: async ({ params }) => {
        const entry = findArticle(params.rubric, params.slug)
        return entry ? { entry, article: (await entry.load()).article } : null
      },
      element: <ArticlePage />,
    },
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
