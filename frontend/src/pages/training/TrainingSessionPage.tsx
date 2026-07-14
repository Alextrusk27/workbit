import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Skeleton } from '@/components/ui/Skeleton'
import { Textarea } from '@/components/ui/Textarea'
import { buttonClasses } from '@/components/ui/buttonStyles'
import { trainingApi } from '@/features/training/api'
import type {
  TrainingQuestion,
  TrainingReport,
  TrainingSession,
} from '@/features/training/api'
import { sessionSubtitle } from '@/features/training/labels'
import {
  useFinishSession,
  useSession,
  useSubmitAnswer,
  useTrainingOptions,
} from '@/features/training/useTraining'
import { ApiRequestError, getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { groupReportCases } from './reportCases'
import { CaseEntry, QuestionEntry, ReportSummary } from './reportParts'

export function TrainingSessionPage() {
  usePageTitle('Тренировка')
  const { sessionId = '' } = useParams()
  const { data: session, isLoading, isError, error } = useSession(sessionId)
  const completedAtMount = useRef<boolean | null>(null)

  if (isLoading) {
    return (
      <Container className="py-10 sm:py-14">
        <div role="status">
          <span className="sr-only">Загрузка тренировки…</span>
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
    return <Navigate to={`/app/training/${sessionId}/report`} replace />
  }

  return <SessionRun key={session.id} session={session} />
}

interface LiveItem {
  q: TrainingQuestion
  answer: string | null
}

function SessionRun({ session }: { session: TrainingSession }) {
  const options = useTrainingOptions()
  const cap = options.data?.questionCap ?? 10
  const min = options.data?.minAnswersToFinish ?? 3

  const [items, setItems] = useState<LiveItem[]>([])
  const [answeredMain, setAnsweredMain] = useState(session.answeredCount)
  const [loadState, setLoadState] = useState<
    'loading' | 'idle' | 'cap' | 'error'
  >('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const resumed = session.answeredCount > 0

  const submit = useSubmitAnswer()
  const finish = useFinishSession()

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
      const q = await trainingApi.nextQuestion(session.id)
      setItems((prev) =>
        prev.some((it) => it.q.questionId === q.questionId)
          ? prev
          : [...prev, { q, answer: null }],
      )
      setLoadState('idle')
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 409) {
        setLoadState('cap')
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
          if (!item.q.followUp) setAnsweredMain((c) => c + 1)
          loadNext()
        },
      },
    )
  }

  const canFinish = answeredMain >= min && !finish.isPending
  const remaining = Math.max(0, min - answeredMain)

  return (
    <Container className="py-10 sm:py-14">
      <div>
        <Link
          to="/app/training"
          className="text-accent hover:text-accent-hover mb-6 inline-block text-sm transition-colors"
        >
          ← Тренажёр
        </Link>
        <div className="flex items-center justify-between">
          <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
            {session.profession}
          </p>
          {!report && (
            <p aria-live="polite" className="text-muted font-mono text-xs">
              {Math.min(answeredMain, cap)} / {cap}
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
                      followUp={item.q.followUp}
                      questionText={item.q.questionText}
                      answerText={item.answer}
                    />
                  </li>
                ),
              )}
            </ol>

            {loadState === 'loading' && (
              <div role="status" className="mt-10">
                <span className="sr-only">Готовим следующий вопрос…</span>
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
              capReached={loadState === 'cap'}
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
  question: TrainingQuestion
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
      {question.followUp ? (
        <span className="bg-accent/10 text-accent rounded-sm px-2 py-0.5 font-mono text-xs">
          Уточняющий вопрос
        </span>
      ) : (
        <p className="text-muted font-mono text-xs">
          Вопрос {question.orderIndex}
        </p>
      )}
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
          тренировки.
        </p>
      </div>
    </div>
  )
}

function FinishBar({
  capReached,
  canFinish,
  remaining,
  pending,
  error,
  onFinish,
}: {
  capReached: boolean
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
          Рецензент читает ваши ответы и готовит итоговую оценку. Это может
          занять несколько секунд.
        </p>
      </div>
    )
  }

  return (
    <div className="border-rule mt-10 border-t pt-8">
      {capReached && (
        <p className="text-muted mb-4 text-sm">
          Достигнут лимит вопросов — тренировку можно завершить.
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
          чтобы завершить тренировку.
        </p>
      )}
    </div>
  )
}

function FinishedView({ report }: { report: TrainingReport }) {
  return (
    <div className="mt-8">
      <ol className="space-y-10">
        {groupReportCases(report.questions).map((c) => (
          <li key={c.main.questionId}>
            <CaseEntry main={c.main} followUps={c.followUps} />
          </li>
        ))}
      </ol>

      <div className="border-rule mt-12 border-t pt-10">
        <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
          Разбор тренировки
        </p>
        <div className="mt-6">
          <ReportSummary
            avgScore={report.avgScore}
            overallFeedback={report.overallFeedback}
          />
        </div>
      </div>

      <div className="mt-12 flex flex-wrap gap-3">
        <Link to="/app/training" className={buttonClasses()}>
          К списку тренировок
        </Link>
        <Link
          to="/app/training/new"
          className={buttonClasses({ variant: 'secondary' })}
        >
          Новая тренировка
        </Link>
      </div>
    </div>
  )
}
