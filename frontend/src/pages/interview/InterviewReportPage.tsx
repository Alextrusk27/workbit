import { Link, useParams } from 'react-router-dom'
import { AppPageHeader } from '@/components/app/AppPageHeader'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import {
  useInterviewReport,
  useInterviewSession,
} from '@/features/interview/useInterview'
import { sessionSubtitle, trainingLevelCode } from '@/features/interview/labels'
import { getErrorMessage } from '@/lib/api'
import { questionsWord } from '@/lib/plural'
import { usePageTitle } from '@/lib/usePageTitle'
import { CaseEntry, ReportSummary } from './reportParts'

export function InterviewReportPage() {
  usePageTitle('Разбор интервью')
  const { sessionId = '' } = useParams()
  const {
    data: report,
    isLoading,
    isError,
    error,
  } = useInterviewReport(sessionId)
  const { data: session } = useInterviewSession(sessionId)

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
    session ? sessionSubtitle(session) : null,
    `${report.questions.length} ${questionsWord(report.questions.length)}`,
  ]
    .filter(Boolean)
    .join(' · ')

  const backTo = session
    ? `/app/interview/vacancy/${session.vacancyId}`
    : '/app/interview'

  const trainingParams = new URLSearchParams({
    skill: report.weakestSkill ?? '',
    level: trainingLevelCode(session?.experience ?? null),
  })

  return (
    <Container>
      <AppPageHeader
        back={{ to: backTo, label: session ? 'Вакансия' : 'Мои интервью' }}
        eyebrow="Разбор интервью"
        title={session?.vacancyName ?? 'Интервью по вакансии'}
      >
        {subtitle}
      </AppPageHeader>

      <div className="mt-8">
        <ReportSummary
          avgScore={report.avgScore}
          offerProbability={report.offerProbability}
          overallFeedback={report.overallFeedback}
          recommendations={report.recommendations}
          weakestSkill={report.weakestSkill}
          trainingTo={`/app/training/new?${trainingParams}`}
          answeredCount={report.questions.length}
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
              <CaseEntry question={q} />
            </li>
          ))}
        </ol>
      </div>

      <div className="mt-12 flex flex-wrap gap-3.5">
        <Link to={backTo} className={buttonClasses({ variant: 'secondary' })}>
          {session ? 'К вакансии' : 'К списку интервью'}
        </Link>
        <Link to="/app/interview/new" className={buttonClasses()}>
          Новое интервью
        </Link>
      </div>
    </Container>
  )
}
