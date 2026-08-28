import { createBrowserRouter, Navigate } from 'react-router-dom'
import { marketingRoute } from '@/app/marketingRoutes'
import { RootLayout } from '@/components/layout/RootLayout'
import { AuthLayout } from '@/components/layout/AuthLayout'
import { AppLayout } from '@/components/layout/AppLayout'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { RedirectIfAuthed } from '@/features/auth/RedirectIfAuthed'
import { ErrorPage } from '@/pages/ErrorPage'
import { LoginPage } from '@/pages/auth/LoginPage'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      marketingRoute,
      {
        path: 'brand',
        errorElement: <ErrorPage />,
        lazy: async () => ({
          Component: (await import('@/pages/BrandPage')).BrandPage,
        }),
      },
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
              {
                index: true,
                lazy: async () => ({
                  Component: (await import('@/pages/HubPage')).HubPage,
                }),
              },
              {
                path: 'billing/success',
                element: <Navigate to="/app?payment=ok" replace />,
              },
              {
                path: 'billing/fail',
                element: <Navigate to="/app?payment=fail" replace />,
              },
              {
                path: 'settings',
                lazy: async () => ({
                  Component: (await import('@/pages/SettingsPage'))
                    .SettingsPage,
                }),
              },
              {
                path: 'training',
                lazy: async () => ({
                  Component: (await import('@/pages/training/TrainingListPage'))
                    .TrainingListPage,
                }),
              },
              {
                path: 'training/new',
                lazy: async () => ({
                  Component: (await import('@/pages/training/NewTrainingPage'))
                    .NewTrainingPage,
                }),
              },
              {
                path: 'training/:sessionId',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/training/TrainingSessionPage')
                  ).TrainingSessionPage,
                }),
              },
              {
                path: 'training/:sessionId/report',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/training/TrainingReportPage')
                  ).TrainingReportPage,
                }),
              },
              {
                path: 'interview',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/interview/InterviewListPage')
                  ).InterviewListPage,
                }),
              },
              {
                path: 'interview/new',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/interview/NewInterviewPage')
                  ).NewInterviewPage,
                }),
              },
              {
                path: 'interview/vacancy/:vacancyId',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/interview/InterviewVacancyPage')
                  ).InterviewVacancyPage,
                }),
              },
              {
                path: 'interview/:sessionId',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/interview/InterviewSessionPage')
                  ).InterviewSessionPage,
                }),
              },
              {
                path: 'interview/:sessionId/report',
                lazy: async () => ({
                  Component: (
                    await import('@/pages/interview/InterviewReportPage')
                  ).InterviewReportPage,
                }),
              },
            ],
          },
        ],
      },
    ],
  },
])
