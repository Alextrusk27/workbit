import { Link, useParams } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { sessionSubtitle } from '@/features/training/labels'
import { useReport } from '@/features/training/useTraining'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { CaseEntry, ReportSummary } from './reportParts'

export function TrainingReportPage() {
  usePageTitle('Разбор тренировки')
  const { sessionId = '' } = useParams()
  const { data: report, isLoading, isError, error } = useReport(sessionId)

  if (isLoading) {
    return (
      <Container>
        <div role="status">
          <span className="sr-only">Загрузка разбора…</span>
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-4 h-9 w-64" />
          <Skeleton className="mt-3 h-4 w-48" />
          <Skeleton className="mt-8 h-28" />
          <Skeleton className="mt-8 h-24" />
        </div>
      </Container>
    )
  }

  if (isError || !report) {
    return (
      <Container>
        <Alert>{getErrorMessage(error)}</Alert>
      </Container>
    )
  }

  const subtitle = [
    sessionSubtitle(report),
    `${report.questions.length} вопросов`,
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <Container>
      <AppPageHeader
        back={{ to: '/app/training', label: 'Тренажёр' }}
        eyebrow="Разбор тренировки"
        title={report.skill}
      >
        {subtitle}
      </AppPageHeader>

      <div className="mt-8">
        <ReportSummary
          avgScore={report.avgScore}
          overallFeedback={report.overallFeedback}
        />
      </div>

      <div className="mt-12">
        <h2 className="text-ink text-[21px] font-bold tracking-[-0.015em]">
          Ответы с пометками
        </h2>
        <ol className="mt-6">
          {report.questions.map((q) => (
            <li
              key={q.questionId}
              className="border-divider mt-8 border-t pt-8 first:mt-0 first:border-0 first:pt-0"
            >
              <CaseEntry question={q} sessionId={report.sessionId} />
            </li>
          ))}
        </ol>
      </div>

      <div className="mt-12 flex flex-wrap gap-3.5">
        <Link
          to="/app/training"
          className={buttonClasses({ variant: 'secondary' })}
        >
          К списку тренировок
        </Link>
        <Link to="/app/training/new" className={buttonClasses()}>
          Новая тренировка
        </Link>
      </div>
    </Container>
  )
}
