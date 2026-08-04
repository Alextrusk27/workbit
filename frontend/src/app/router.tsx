import { createBrowserRouter, Navigate } from 'react-router-dom'
import App from '@/App'
import { RootLayout } from '@/components/layout/RootLayout'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { AppLayout } from '@/components/layout/AppLayout'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { RedirectIfAuthed } from '@/features/auth/RedirectIfAuthed'
import { HomePage } from '@/pages/HomePage'
import { AiInterviewPage } from '@/pages/AiInterviewPage'
import { SkillsTrainerPage } from '@/pages/SkillsTrainerPage'
import { FaqPage } from '@/pages/FaqPage'
import { PricingPage } from '@/pages/PricingPage'
import { NotFoundPage } from '@/pages/NotFoundPage'
import { ErrorPage } from '@/pages/ErrorPage'
import { BrandPage } from '@/pages/BrandPage'
import { LoginPage } from '@/pages/auth/LoginPage'
import { HubPage } from '@/pages/HubPage'
import { SettingsPage } from '@/pages/SettingsPage'
import { TrainingListPage } from '@/pages/training/TrainingListPage'
import { NewTrainingPage } from '@/pages/training/NewTrainingPage'
import { TrainingSessionPage } from '@/pages/training/TrainingSessionPage'
import { TrainingReportPage } from '@/pages/training/TrainingReportPage'
import { InterviewListPage } from '@/pages/interview/InterviewListPage'
import { NewInterviewPage } from '@/pages/interview/NewInterviewPage'
import { InterviewVacancyPage } from '@/pages/interview/InterviewVacancyPage'
import { InterviewSessionPage } from '@/pages/interview/InterviewSessionPage'
import { InterviewReportPage } from '@/pages/interview/InterviewReportPage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
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
      },
      { path: 'brand', element: <BrandPage />, errorElement: <ErrorPage /> },
      {
        element: <AuthLayout />,
        errorElement: <ErrorPage />,
        children: [
          {
            element: <RedirectIfAuthed />,
            children: [{ path: 'login', element: <LoginPage /> }],
          },
          { path: 'register', element: <Navigate to="/login" replace /> },
        ],
      },
      {
        path: 'app',
        element: <RequireAuth />,
        errorElement: <ErrorPage />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { index: true, element: <HubPage /> },
              { path: 'settings', element: <SettingsPage /> },
              { path: 'training', element: <TrainingListPage /> },
              { path: 'training/new', element: <NewTrainingPage /> },
              {
                path: 'training/:sessionId',
                element: <TrainingSessionPage />,
              },
              {
                path: 'training/:sessionId/report',
                element: <TrainingReportPage />,
              },
              { path: 'interview', element: <InterviewListPage /> },
              { path: 'interview/new', element: <NewInterviewPage /> },
              {
                path: 'interview/vacancy/:vacancyId',
                element: <InterviewVacancyPage />,
              },
              {
                path: 'interview/:sessionId',
                element: <InterviewSessionPage />,
              },
              {
                path: 'interview/:sessionId/report',
                element: <InterviewReportPage />,
              },
            ],
          },
        ],
      },
    ],
  },
])
