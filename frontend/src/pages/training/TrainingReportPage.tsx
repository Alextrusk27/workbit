import { Link, useParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { sessionSubtitle } from '@/features/training/labels'
import { useReport } from '@/features/training/useTraining'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { QuestionEntry, ReportSummary } from './reportParts'

export function TrainingReportPage() {
  usePageTitle('Разбор тренировки')
  const { sessionId = '' } = useParams()
  const { data: report, isLoading, isError, error } = useReport(sessionId)

  if (isLoading) {
    return (
      <Container className="py-12 sm:py-16">
        <div role="status">
          <span className="sr-only">Загрузка разбора…</span>
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-4 h-9 w-64" />
          <Skeleton className="mt-3 h-4 w-48" />
          <Skeleton className="mt-8 h-28 sm:max-w-xs" />
          <Skeleton className="mt-8 h-24" />
        </div>
      </Container>
    )
  }

  if (isError || !report) {
    return (
      <Container className="py-16">
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
    <Container className="py-12 sm:py-16">
      <Link
        to="/app/training"
        className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
      >
        ← Тренажёр
      </Link>
      <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
        Разбор тренировки
      </p>
      <h1 className="text-ink mt-4 text-3xl break-words sm:text-4xl">
        {report.profession}
      </h1>
      <p className="text-muted mt-2 text-sm">{subtitle}</p>

      <div className="mt-8">
        <ReportSummary
          avgScore={report.avgScore}
          overallFeedback={report.overallFeedback}
        />
      </div>

      <div className="mt-12">
        <h2 className="text-ink font-display text-xl">Ответы с пометками</h2>
        <ol className="mt-6 space-y-8">
          {report.questions.map((q) => (
            <li key={q.questionId}>
              <QuestionEntry
                orderIndex={q.orderIndex}
                followUp={q.followUp}
                questionText={q.questionText}
                answerText={q.answerText}
                score={q.score}
                feedback={q.feedback}
              />
            </li>
          ))}
        </ol>
      </div>

      <div className="mt-12">
        <Link
          to="/app/training"
          className={buttonClasses({ variant: 'secondary' })}
        >
          К списку тренировок
        </Link>
      </div>
    </Container>
  )
}
