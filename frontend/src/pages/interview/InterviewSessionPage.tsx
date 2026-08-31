import type { KeyboardEvent } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { ChatShell } from '@/components/chat/ChatShell'
import { TypingDots } from '@/components/chat/TypingDots'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { Skeleton } from '@/components/ui/Skeleton'
import { Spinner } from '@/components/ui/Spinner'
import { IconSend } from '@/components/marketing/icons'
import { DictationHints } from '@/components/speech/DictationHints'
import { MicButton } from '@/components/speech/MicButton'
import {
  interviewApi,
  type InterviewQuestion,
  type InterviewSession,
} from '@/features/interview/api'
import { sessionSubtitle } from '@/features/interview/labels'
import {
  useFinishInterview,
  useInterviewSession,
  useSubmitInterviewAnswer,
} from '@/features/interview/useInterview'
import { useDictatedAnswer } from '@/features/speech/useDictatedAnswer'
import { ApiRequestError, getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'
import { useUnsavedAnswerGuard } from '@/lib/useUnsavedAnswerGuard'

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
      <Container>
        <div role="status">
          <span className="sr-only">Загрузка интервью…</span>
          <Skeleton className="h-3 w-48" />
          <Skeleton className="mt-8 h-8 w-3/4" />
          <Skeleton className="mt-6 h-96 w-full" />
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
    return <Navigate to={`/app/interview/${sessionId}/report`} replace />
  }

  return <SessionRun key={session.id} session={session} />
}

interface LiveItem {
  q: InterviewQuestion
  answer: string | null
}

function SessionRun({ session }: { session: InterviewSession }) {
  const navigate = useNavigate()
  const [items, setItems] = useState<LiveItem[]>([])
  const [answered, setAnswered] = useState(session.answeredCount)
  const [loadState, setLoadState] = useState<
    'loading' | 'idle' | 'done' | 'error'
  >('loading')
  const [loadError, setLoadError] = useState<string | null>(null)

  const submit = useSubmitInterviewAnswer()
  const finish = useFinishInterview()

  const startedRef = useRef(false)
  const finishStartedRef = useRef(false)
  const inFlight = useRef(false)
  const historyLoaded = useRef(session.answeredCount === 0)
  const bodyRef = useRef<HTMLDivElement>(null)

  const loadNext = useCallback(async () => {
    if (inFlight.current) return
    inFlight.current = true
    setLoadState('loading')
    setLoadError(null)
    try {
      if (!historyLoaded.current) {
        const past = await interviewApi.answeredQuestions(session.id)
        setItems(past.map((q) => ({ q, answer: q.answerText })))
        historyLoaded.current = true
      }
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

  // Все основные вопросы отвечены (next вернул 409) — завершаем сами и уходим
  // на страницу разбора; guard-ref защищает от повторного вызова.
  const finishMutate = finish.mutate
  const finishSession = useCallback(() => {
    finishMutate(session.id, {
      onSuccess: () =>
        navigate(`/app/interview/${session.id}/report`, { replace: true }),
    })
  }, [finishMutate, navigate, session.id])

  useEffect(() => {
    if (loadState !== 'done' || finishStartedRef.current) return
    finishStartedRef.current = true
    finishSession()
  }, [loadState, finishSession])

  useEffect(() => {
    const body = bodyRef.current
    if (body) body.scrollTop = body.scrollHeight
  }, [items, loadState])

  const current = items.find((it) => it.answer === null) ?? null

  const onSend = (text: string) => {
    if (!current) return
    return submit
      .mutateAsync({
        sessionId: session.id,
        questionId: current.q.questionId,
        answerText: text,
      })
      .then(() => {
        setItems((prev) =>
          prev.map((it) =>
            it.q.questionId === current.q.questionId
              ? { ...it, answer: text }
              : it,
          ),
        )
        if (!current.q.followUp) setAnswered((c) => c + 1)
        loadNext()
      })
  }

  const finishing = loadState === 'done'

  return (
    <Container>
      <Link
        to={`/app/interview/vacancy/${session.vacancyId}`}
        className="text-indigo hover:text-violet mb-7 inline-block text-sm transition-colors"
      >
        ← Вакансия
      </Link>

      <div className="flex items-baseline justify-between gap-4">
        <Eyebrow className="break-words">{session.vacancyName}</Eyebrow>
        <p
          aria-live="polite"
          className="text-dim text-[13px] whitespace-nowrap tabular-nums"
        >
          {Math.min(answered, session.totalQuestions)} /{' '}
          {session.totalQuestions}
        </p>
      </div>
      <p className="text-muted mt-1.5 text-sm">{sessionSubtitle(session)}</p>

      <ChatShell
        className="mt-6"
        name="AI-интервьюер"
        status={finishing ? 'формируем разбор' : 'интервью идёт'}
        bodyRef={bodyRef}
        bodyClassName="h-[min(56svh,500px)]"
        footer={
          <Composer
            disabled={!current || finishing}
            pending={submit.isPending}
            onSend={onSend}
          />
        }
      >
        {items.map((item) => (
          <ChatMessages key={item.q.questionId} item={item} items={items} />
        ))}

        {loadState === 'loading' && (
          <ChatBubble role="bot">
            <TypingDots />
          </ChatBubble>
        )}

        {finishing && !finish.isError && (
          <ChatBubble role="bot" who="Интервью завершено">
            <Spinner className="mr-2.5" />
            Спасибо, это был последний вопрос. Формирую разбор: оценки по
            каждому ответу, правки и вероятность оффера…
          </ChatBubble>
        )}
      </ChatShell>

      <p className="text-dim mt-3 text-[12.5px]">
        Enter — отправить · Shift+Enter — перенос
      </p>

      {submit.isError && (
        <div className="mt-4">
          <Alert>{getErrorMessage(submit.error)}</Alert>
        </div>
      )}

      {loadState === 'error' && (
        <div className="mt-4">
          <Alert>{loadError}</Alert>
          <Button variant="secondary" className="mt-4" onClick={loadNext}>
            Повторить
          </Button>
        </div>
      )}

      {finish.isError && (
        <div className="mt-4">
          <Alert>{getErrorMessage(finish.error)}</Alert>
          <Button variant="secondary" className="mt-4" onClick={finishSession}>
            Повторить
          </Button>
        </div>
      )}
    </Container>
  )
}

/** Пара сообщений: вопрос рецензента и ответ кандидата, если он уже дан.
 *  Уточняющий вопрос показывается с цитатой ответа, к которому он задан. */
function ChatMessages({ item, items }: { item: LiveItem; items: LiveItem[] }) {
  const index = items.indexOf(item)
  const previousAnswer = items
    .slice(0, index)
    .filter((it) => it.answer !== null)
    .at(-1)?.answer

  return (
    <>
      <ChatBubble
        role="bot"
        who={
          item.q.followUp ? 'Уточняющий вопрос' : `Вопрос ${item.q.orderIndex}`
        }
        quote={
          item.q.followUp && previousAnswer
            ? { name: 'Ты', text: previousAnswer }
            : undefined
        }
      >
        {item.q.questionText}
      </ChatBubble>
      {item.answer !== null && (
        <ChatBubble role="user" who="Ты">
          {item.answer}
        </ChatBubble>
      )}
    </>
  )
}

function Composer({
  disabled,
  pending,
  onSend,
}: {
  disabled: boolean
  pending: boolean
  onSend: (text: string) => void | Promise<unknown>
}) {
  const {
    text,
    setText,
    dictation,
    recording,
    busy,
    canSend,
    send,
    toggleMic,
  } = useDictatedAnswer(onSend, { disabled, pending })
  const ref = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    const ta = ref.current
    if (!ta) return
    ta.style.height = 'auto'
    ta.style.height = `${Math.min(ta.scrollHeight, 120)}px`
  }, [text])

  useUnsavedAnswerGuard(text)

  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  return (
    <>
      <MicButton
        recording={recording}
        disabled={disabled || pending || dictation.state === 'stopping'}
        onClick={toggleMic}
      />

      <div className="min-w-0 flex-1">
        <textarea
          ref={ref}
          rows={1}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={onKeyDown}
          disabled={disabled || pending}
          aria-label="Твой ответ"
          placeholder={
            disabled
              ? 'Дождись следующего вопроса…'
              : 'Отвечай так, как отвечал бы на собеседовании…'
          }
          className="border-line bg-surface text-ink placeholder:text-dim focus:border-indigo focus:ring-indigo/18 max-h-30 min-h-9.5 w-full resize-none rounded-md border px-3 py-2.5 text-[13.5px] transition-colors focus:ring-[3px] focus:outline-none disabled:opacity-60"
        />
        <DictationHints dictation={dictation} />
      </div>

      <button
        type="button"
        onClick={send}
        disabled={!canSend}
        aria-label={pending || busy ? 'Отправляем ответ' : 'Отправить ответ'}
        className="bg-grad grid size-9 shrink-0 place-items-center rounded-full text-white shadow-[0_4px_14px_rgba(99,102,241,0.35)] transition hover:-translate-y-px disabled:pointer-events-none disabled:opacity-45"
      >
        {pending || busy ? (
          <Spinner className="size-4 border-white" />
        ) : (
          <IconSend className="size-[15px]" />
        )}
      </button>
    </>
  )
}
