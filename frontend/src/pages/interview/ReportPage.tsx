import { Link, useParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Container } from '@/components/ui/Container'
import { MarginNote } from '@/components/ui/MarginNote'
import { Skeleton } from '@/components/ui/Skeleton'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { useReport, useTranscript } from '@/features/interview/useInterview'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function ReportPage() {
  usePageTitle('Отчёт по интервью')
  const { sessionId = '' } = useParams()
  const { data: report, isLoading, isError, error } = useReport(sessionId)
  const transcript = useTranscript(sessionId, report?.totalQuestions ?? 0)

  if (isLoading) {
    return (
      <Container className="py-12 sm:py-16">
        <div role="status" className="mx-auto max-w-2xl">
          <span className="sr-only">Загрузка отчёта…</span>
          <Skeleton className="h-4 w-32" />
          <Skeleton className="mt-4 h-9 w-64" />
          <Skeleton className="mt-3 h-4 w-48" />
          <div className="mt-8 grid grid-cols-2 gap-4">
            <Skeleton className="h-28" />
            <Skeleton className="h-28" />
          </div>
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

  return (
    <Container className="py-12 sm:py-16">
      <div className="mx-auto max-w-2xl">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Разбор интервью
        </p>
        <h1 className="text-ink mt-4 text-3xl sm:text-4xl">
          {report.profession}
        </h1>
        <p className="text-muted mt-2 text-sm">
          {report.level} · {report.companyType} · {report.totalQuestions}{' '}
          вопросов
        </p>

        <div className="mt-8 grid grid-cols-2 gap-4">
          <div className="border-rule bg-paper-2/60 rounded-lg border p-5">
            <p className="text-muted text-xs">Средний балл</p>
            <p className="text-ink mt-1 font-mono text-3xl">
              {report.avgScore.toFixed(1)}
              <span className="text-muted text-lg">/10</span>
            </p>
          </div>
          <div className="border-rule bg-paper-2/60 rounded-lg border p-5">
            <p className="text-muted text-xs">Вероятность оффера</p>
            <p className="text-ink font-display mt-1 text-2xl">
              {report.offerProbability}
            </p>
          </div>
        </div>

        <div className="mt-8">
          <h2 className="text-ink font-display text-xl">Итог рецензента</h2>
          <p className="text-ink mt-3 leading-relaxed whitespace-pre-wrap">
            {report.overallFeedback}
          </p>
        </div>

        <div className="mt-12">
          <h2 className="text-ink font-display text-xl">Ответы с пометками</h2>
          {transcript.isLoading ? (
            <div role="status" className="mt-6 space-y-8">
              <span className="sr-only">Загрузка ответов…</span>
              {[0, 1].map((i) => (
                <div key={i}>
                  <Skeleton className="h-3 w-20" />
                  <Skeleton className="mt-2 h-5 w-3/4" />
                  <Skeleton className="mt-3 h-4 w-full" />
                </div>
              ))}
            </div>
          ) : (
            <ol className="mt-6 space-y-8">
              {transcript.questions.map((q) => (
                <li key={q.questionId}>
                  <p className="text-muted font-mono text-xs">
                    Вопрос {q.orderIndex}
                  </p>
                  <h3 className="text-ink font-display mt-1 text-lg leading-snug">
                    {q.questionText}
                  </h3>
                  <p className="text-ink mt-3 whitespace-pre-wrap">
                    {q.answerText || (
                      <span className="text-muted italic">Без ответа</span>
                    )}
                  </p>
                  {q.feedback && (
                    <MarginNote score={q.score ?? undefined} className="mt-4">
                      {q.feedback}
                    </MarginNote>
                  )}
                </li>
              ))}
            </ol>
          )}
        </div>

        <div className="mt-12">
          <Link to="/app" className={buttonClasses({ variant: 'secondary' })}>
            К списку интервью
          </Link>
        </div>
      </div>
    </Container>
  )
}
