import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Textarea } from '@/components/ui/Textarea'
import { buttonClasses } from '@/components/ui/buttonStyles'
import {
  interviewApi,
  MIN_ANSWERS_TO_FINISH,
  type InterviewQuestion,
  type InterviewReport,
  type InterviewSession,
} from '@/features/interview/api'
import { sessionSubtitle } from '@/features/interview/labels'
import {
  useFinishInterview,
  useInterviewSession,
  useSubmitInterviewAnswer,
} from '@/features/interview/useInterview'
import { ApiRequestError, getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { CaseEntry, QuestionEntry, ReportSummary } from './reportParts'

export function InterviewSessionPage() {
  usePageTitle('Интервью')
  const { sessionId = '' } = useParams()
  const {
    data: session,
    isLoading,
    isError,
    error,
  } = useInterviewSession(sessionId)
  const completedAtMount = useRef<boolean | null>(null)

  if (isLoading) {
    return (
      <Container className="py-10 sm:py-14">
        <div role="status">
          <span className="sr-only">Загрузка интервью…</span>
          <Skeleton className="h-3 w-48" />
          <Skeleton className="mt-8 h-8 w-3/4" />
          <Skeleton className="mt-6 h-32 w-full" />
        </div>
      </Container>
    )
  }

  if (isError || !session) {
    return (
      <Container className="py-16">
        <Alert>{getErrorMessage(error)}</Alert>
      </Container>
    )
  }

  if (completedAtMount.current === null) {
    completedAtMount.current = session.status === 'COMPLETED'
  }
  if (completedAtMount.current) {
    return <Navigate to={`/app/interview/${sessionId}/report`} replace />
  }

  return <SessionRun key={session.id} session={session} />
}

interface LiveItem {
  q: InterviewQuestion
  answer: string | null
}

function SessionRun({ session }: { session: InterviewSession }) {
  const [items, setItems] = useState<LiveItem[]>([])
  const [answered, setAnswered] = useState(session.answeredCount)
  const [loadState, setLoadState] = useState<
    'loading' | 'idle' | 'done' | 'error'
  >('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const resumed = session.answeredCount > 0

  const submit = useSubmitInterviewAnswer()
  const finish = useFinishInterview()

  const report = finish.data ?? null
  const startedRef = useRef(false)
  const inFlight = useRef(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  const loadNext = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    setLoadState('loading')
    setLoadError(null)
    try {
      const q = await interviewApi.nextQuestion(session.id)
      setItems((prev) =>
        prev.some((it) => it.q.questionId === q.questionId)
          ? prev
          : [...prev, { q, answer: null }],
      )
      setLoadState('idle')
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 409) {
        setLoadState('done')
      } else {
        setLoadState('error')
        setLoadError(getErrorMessage(e))
      }
    } finally {
      inFlight.current = false
    }
  }, [session.id])

  useEffect(() => {
    if (startedRef.current) return
    startedRef.current = true
    loadNext()
  }, [loadNext])

  useEffect(() => {
    if (!bottomRef.current) return
    const reduced = window.matchMedia(
      '(prefers-reduced-motion: reduce)',
    ).matches
    bottomRef.current.scrollIntoView({
      behavior: reduced ? 'auto' : 'smooth',
      block: 'end',
    })
  }, [items.length, report])

  const onAnswer = (item: LiveItem, text: string) => {
    submit.mutate(
      {
        sessionId: session.id,
        questionId: item.q.questionId,
        answerText: text,
      },
      {
        onSuccess: () => {
          setItems((prev) =>
            prev.map((it) =>
              it.q.questionId === item.q.questionId
                ? { ...it, answer: text }
                : it,
            ),
          )
          setAnswered((c) => c + 1)
          loadNext()
        },
      },
    )
  }

  const canFinish = answered >= MIN_ANSWERS_TO_FINISH && !finish.isPending
  const remaining = Math.max(0, MIN_ANSWERS_TO_FINISH - answered)

  return (
    <Container className="py-10 sm:py-14">
      <div>
        <Link
          to="/app/interview"
          className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
        >
          ← Интервью
        </Link>
        <div className="flex items-center justify-between gap-4">
          <p className="text-muted font-mono text-xs tracking-[0.2em] break-words uppercase">
            {session.vacancyName}
          </p>
          {!report && (
            <p
              aria-live="polite"
              className="text-muted shrink-0 font-mono text-xs"
            >
              Отвечено: {answered}
            </p>
          )}
        </div>
        <p className="text-muted mt-1 text-sm">{sessionSubtitle(session)}</p>

        {resumed && !report && (
          <p className="text-muted mt-4 text-xs">
            Прошлые ответы этой сессии появятся в разборе после завершения.
          </p>
        )}

        {report ? (
          <FinishedView report={report} />
        ) : (
          <>
            <ol className="mt-8 space-y-10">
              {items.map((item) =>
                item.answer === null ? (
                  <li key={item.q.questionId}>
                    <CurrentQuestion
                      question={item.q}
                      pending={submit.isPending}
                      error={
                        submit.isError ? getErrorMessage(submit.error) : null
                      }
                      onSubmit={(text) => onAnswer(item, text)}
                    />
                  </li>
                ) : (
                  <li key={item.q.questionId}>
                    <QuestionEntry
                      orderIndex={item.q.orderIndex}
                      questionText={item.q.questionText}
                      answerText={item.answer}
                    />
                  </li>
                ),
              )}
            </ol>

            {loadState === 'loading' && (
              <div role="status" className="mt-10">
                <span className="sr-only">Загружаем следующий вопрос…</span>
                <Skeleton className="h-6 w-2/3" />
                <Skeleton className="mt-4 h-24 w-full" />
              </div>
            )}

            {loadState === 'error' && (
              <div className="mt-8">
                <Alert>{loadError}</Alert>
                <Button variant="secondary" className="mt-4" onClick={loadNext}>
                  Повторить
                </Button>
              </div>
            )}

            <FinishBar
              allAnswered={loadState === 'done'}
              canFinish={canFinish}
              remaining={remaining}
              pending={finish.isPending}
              error={finish.isError ? getErrorMessage(finish.error) : null}
              onFinish={() => finish.mutate(session.id)}
            />
          </>
        )}

        <div ref={bottomRef} />
      </div>
    </Container>
  )
}

function CurrentQuestion({
  question,
  pending,
  error,
  onSubmit,
}: {
  question: InterviewQuestion
  pending: boolean
  error: string | null
  onSubmit: (text: string) => void
}) {
  const [answer, setAnswer] = useState('')

  useEffect(() => {
    if (!answer.trim()) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [answer])

  return (
    <div>
      <p className="text-muted font-mono text-xs">
        Вопрос {question.orderIndex}
      </p>
      <h1 className="text-ink font-display mt-1.5 text-2xl leading-snug break-words">
        {question.questionText}
      </h1>

      <div className="mt-6">
        <Textarea
          label="Ваш ответ"
          value={answer}
          onChange={(e) => setAnswer(e.target.value)}
          placeholder="Отвечайте так, как отвечали бы на собеседовании…"
          disabled={pending}
        />

        {error && (
          <div className="mt-4">
            <Alert>{error}</Alert>
          </div>
        )}

        <Button
          size="lg"
          className="mt-5"
          onClick={() => answer.trim() && onSubmit(answer)}
          disabled={!answer.trim() || pending}
        >
          {pending ? 'Сохраняем ответ…' : 'Ответить'}
        </Button>
        <p className="text-muted mt-3 text-xs">
          Оценок по ходу нет — весь разбор придёт в конце, при завершении
          интервью.
        </p>
      </div>
    </div>
  )
}

function FinishBar({
  allAnswered,
  canFinish,
  remaining,
  pending,
  error,
  onFinish,
}: {
  allAnswered: boolean
  canFinish: boolean
  remaining: number
  pending: boolean
  error: string | null
  onFinish: () => void
}) {
  if (pending) {
    return (
      <div className="border-rule mt-10 border-t pt-8 text-center">
        <p role="status" className="text-ink font-display text-xl">
          Формируем разбор…
        </p>
        <p className="text-muted mx-auto mt-2 max-w-md text-sm">
          Рецензент читает ваши ответы, оценивает их и прикидывает шансы на
          оффер. Это может занять несколько секунд.
        </p>
      </div>
    )
  }

  return (
    <div className="border-rule mt-10 border-t pt-8">
      {allAnswered && (
        <p className="text-muted mb-4 text-sm">
          Вопросы закончились — интервью можно завершить.
        </p>
      )}
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}
      {canFinish ? (
        <Button size="lg" variant="secondary" onClick={onFinish}>
          Завершить и получить разбор
        </Button>
      ) : (
        <p className="text-muted text-sm">
          Ответьте ещё на {remaining}{' '}
          {remaining === 1 ? 'вопрос' : remaining < 5 ? 'вопроса' : 'вопросов'},
          чтобы завершить интервью.
        </p>
      )}
    </div>
  )
}

function FinishedView({ report }: { report: InterviewReport }) {
  return (
    <div className="mt-8">
      <ol className="space-y-10">
        {report.questions.map((q) => (
          <li key={q.questionId}>
            <CaseEntry question={q} />
          </li>
        ))}
      </ol>

      <div className="border-rule mt-12 border-t pt-10">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Разбор интервью
        </p>
        <div className="mt-6">
          <ReportSummary
            avgScore={report.avgScore}
            offerProbability={report.offerProbability}
            overallFeedback={report.overallFeedback}
          />
        </div>
      </div>

      <div className="mt-12 flex flex-wrap gap-3">
        <Link to="/app/interview" className={buttonClasses()}>
          К списку интервью
        </Link>
        <Link
          to="/app/interview/new"
          className={buttonClasses({ variant: 'secondary' })}
        >
          Новое интервью
        </Link>
      </div>
    </div>
  )
}
