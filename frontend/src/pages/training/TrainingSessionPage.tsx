import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { DictationHints } from '@/components/speech/DictationHints'
import { MicButton } from '@/components/speech/MicButton'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Skeleton } from '@/components/ui/Skeleton'
import { Spinner } from '@/components/ui/Spinner'
import { Textarea } from '@/components/ui/Textarea'
import { useQuota } from '@/features/billing/useBilling'
import { useDictatedAnswer } from '@/features/speech/useDictatedAnswer'
import { trainingApi } from '@/features/training/api'
import type { TrainingQuestion, TrainingSession } from '@/features/training/api'
import { trainingErrorMessage } from '@/features/training/errors'
import { sessionSubtitle } from '@/features/training/labels'
import {
  useAddQuestions,
  useFinishSession,
  useSession,
  useSubmitAnswer,
  useTrainingOptions,
} from '@/features/training/useTraining'
import { ApiRequestError, getErrorMessage } from '@/lib/api'
import { questionsWord } from '@/lib/plural'
import { usePageTitle } from '@/lib/usePageTitle'
import { QuestionEntry, ReferenceAnswer } from './reportParts'

export function TrainingSessionPage() {
  usePageTitle('Тренировка')
  const { sessionId = '' } = useParams()
  const { data: session, isLoading, isError, error } = useSession(sessionId)
  const completedAtMount = useRef<boolean | null>(null)

  if (isLoading) {
    return (
      <Container>
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
      <Container>
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
  const navigate = useNavigate()
  const options = useTrainingOptions()
  const quota = useQuota()
  const batch = options.data?.questionCap ?? 10
  const maxQuestions = options.data?.maxQuestions ?? 50
  const min = options.data?.minAnswersToFinish ?? 3

  const [items, setItems] = useState<LiveItem[]>([])
  const [answeredMain, setAnsweredMain] = useState(session.answeredCount)
  const [total, setTotal] = useState(session.totalQuestions)
  const [loadState, setLoadState] = useState<
    'loading' | 'idle' | 'cap' | 'error'
  >('loading')
  const [loadError, setLoadError] = useState<string | null>(null)
  const resumed = session.answeredCount > 0

  const submit = useSubmitAnswer()
  const finish = useFinishSession()
  const more = useAddQuestions()

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
  }, [items.length])

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
          setAnsweredMain((c) => c + 1)
          loadNext()
        },
      },
    )
  }

  const onFinish = () => {
    finish.mutate(session.id, {
      onSuccess: () =>
        navigate(`/app/training/${session.id}/report`, { replace: true }),
    })
  }

  const onAddQuestions = () => {
    more.mutate(session.id, {
      onSuccess: (updated) => {
        setTotal(updated.totalQuestions)
        loadNext()
      },
    })
  }

  const canFinish = answeredMain >= min && !finish.isPending
  const remaining = Math.max(0, min - answeredMain)

  return (
    <Container>
      <div className="flex items-baseline justify-between gap-4">
        <Eyebrow>{session.skill}</Eyebrow>
        <p
          aria-live="polite"
          className="text-dim text-[13px] whitespace-nowrap tabular-nums"
        >
          {Math.min(answeredMain, total)} / {total}
        </p>
      </div>
      <p className="text-muted mt-1.5 text-sm">{sessionSubtitle(session)}</p>

      {resumed && (
        <p className="text-dim mt-4 text-xs">
          Прошлые ответы этой сессии появятся в разборе после завершения.
        </p>
      )}

      <ol className="mt-9">
        {items.map((item) => (
          <li
            key={item.q.questionId}
            className="border-divider mt-8 border-t pt-8 first:mt-0 first:border-0 first:pt-0"
          >
            {item.answer === null ? (
              <CurrentQuestion
                question={item.q}
                pending={submit.isPending}
                error={submit.isError ? getErrorMessage(submit.error) : null}
                onSubmit={(text) => onAnswer(item, text)}
              />
            ) : (
              <>
                <QuestionEntry
                  orderIndex={item.q.orderIndex}
                  questionText={item.q.questionText}
                  answerText={item.answer}
                />
                <ReferenceAnswer
                  sessionId={session.id}
                  questionId={item.q.questionId}
                  withFeedback
                />
              </>
            )}
          </li>
        ))}
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
        onFinish={onFinish}
        batch={Math.min(batch, maxQuestions - total)}
        upsell={quota.data?.plan === 'FREE'}
        addPending={more.isPending}
        addError={more.isError ? trainingErrorMessage(more.error) : null}
        onAddQuestions={onAddQuestions}
      />

      <div ref={bottomRef} />
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
  const { text, setText, dictation, recording, canSend, send, toggleMic } =
    useDictatedAnswer(onSubmit, { disabled: false, pending })

  useEffect(() => {
    if (!text.trim()) return
    const warn = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [text])

  return (
    <div>
      <Eyebrow className="tracking-[0.08em]">
        Вопрос {question.orderIndex}
      </Eyebrow>
      <h1 className="text-ink mt-2 text-[21px] leading-snug font-bold break-words">
        {question.questionText}
      </h1>

      <div className="mt-5.5">
        <Textarea
          label="Ваш ответ"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Отвечайте так, как отвечали бы на собеседовании…"
          disabled={pending}
        />
        <DictationHints dictation={dictation} />

        {error && (
          <div className="mt-4">
            <Alert>{error}</Alert>
          </div>
        )}

        <div className="mt-4.5 flex items-center gap-3">
          <Button onClick={send} disabled={!canSend}>
            {pending ? 'Сохраняем ответ…' : 'Ответить'}
          </Button>
          <MicButton
            recording={recording}
            disabled={pending || dictation.state === 'stopping'}
            onClick={toggleMic}
          />
        </div>
        <p className="text-dim mt-3 text-[12.5px]">
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
  batch,
  upsell,
  addPending,
  addError,
  onAddQuestions,
}: {
  capReached: boolean
  canFinish: boolean
  remaining: number
  pending: boolean
  error: string | null
  onFinish: () => void
  batch: number
  upsell: boolean
  addPending: boolean
  addError: string | null
  onAddQuestions: () => void
}) {
  if (pending) {
    return (
      <div className="border-divider mt-12 border-t pt-9 text-center">
        <p role="status" className="text-ink text-[19px] font-bold">
          <Spinner className="mr-2.5" />
          Формируем разбор…
        </p>
        <p className="text-muted mx-auto mt-2.5 max-w-[46ch] text-sm">
          Рецензент читает ваши ответы и оценивает их. Это может занять
          несколько секунд.
        </p>
      </div>
    )
  }

  return (
    <div className="border-divider mt-12 border-t pt-9">
      {capReached && (
        <p className="text-muted mb-4 text-sm">
          {batch <= 0
            ? 'Достигнут потолок вопросов в одной тренировке — её можно завершить.'
            : upsell
              ? 'Вопросы закончились — завершите тренировку и получите разбор.'
              : `Вопросы закончились — возьмите ещё ${batch} ${questionsWord(batch)} или завершите тренировку и получите разбор.`}
        </p>
      )}
      {error && (
        <div className="mb-4">
          <Alert>{error}</Alert>
        </div>
      )}
      {addError && (
        <div className="mb-4">
          <Alert>{addError}</Alert>
        </div>
      )}
      <div className="flex flex-wrap items-center gap-3.5">
        {capReached &&
          batch > 0 &&
          (upsell ? (
            <p className="text-dim text-sm">
              Добор вопросов доступен на тарифах{' '}
              <Link
                to="/pricing"
                className="text-indigo hover:text-violet transition-colors"
              >
                Про и Макс
              </Link>
              .
            </p>
          ) : (
            <Button
              variant="secondary"
              onClick={onAddQuestions}
              disabled={addPending}
            >
              {addPending ? (
                <>
                  <Spinner className="mr-2" />
                  Подбираем вопросы…
                </>
              ) : (
                `Ещё ${batch} ${questionsWord(batch)}`
              )}
            </Button>
          ))}
        {canFinish ? (
          <Button variant="secondary" onClick={onFinish}>
            Завершить и получить разбор
          </Button>
        ) : (
          <p className="text-muted text-sm">
            Ответьте ещё на {remaining} {questionsWord(remaining)}, чтобы
            завершить тренировку.
          </p>
        )}
      </div>
    </div>
  )
}
