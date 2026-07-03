import { useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router-dom'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Container } from '@/components/ui/Container'
import { MarginNote } from '@/components/ui/MarginNote'
import { Textarea } from '@/components/ui/Textarea'
import type { SessionResponse } from '@/features/interview/api'
import {
  useFinishSession,
  useQuestion,
  useSession,
  useSubmitAnswer,
} from '@/features/interview/useInterview'
import { getErrorMessage } from '@/lib/api'
import { usePageTitle } from '@/lib/usePageTitle'

export function InterviewSessionPage() {
  usePageTitle('Интервью')
  const { sessionId = '' } = useParams()
  const { data: session, isLoading, isError, error } = useSession(sessionId)

  if (isLoading) {
    return (
      <Container className="py-16">
        <p className="text-muted text-sm">Загрузка…</p>
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

  if (session.status === 'COMPLETED') {
    return <Navigate to={`/app/interview/${sessionId}/report`} replace />
  }

  return <SessionRunner session={session} />
}

function SessionRunner({ session }: { session: SessionResponse }) {
  const navigate = useNavigate()
  const total = session.totalQuestions
  const [index, setIndex] = useState(session.answeredCount + 1)
  const finish = useFinishSession()

  const doFinish = () => {
    finish.mutate(session.id, {
      onSuccess: () =>
        navigate(`/app/interview/${session.id}/report`, { replace: true }),
    })
  }

  const goForward = () => {
    if (index >= total) doFinish()
    else setIndex((i) => i + 1)
  }

  if (finish.isPending) {
    return (
      <Container className="py-16 text-center">
        <p className="text-ink font-display text-xl">Формируем разбор…</p>
        <p className="text-muted mx-auto mt-2 max-w-md text-sm">
          Рецензент читает ваши ответы и готовит итоговую оценку. Это может
          занять несколько секунд.
        </p>
      </Container>
    )
  }

  return (
    <Container className="py-10 sm:py-14">
      <div className="mx-auto max-w-2xl">
        <div className="flex items-center justify-between">
          <p className="text-muted font-mono text-xs tracking-[0.2em] uppercase">
            {session.profession} · {session.level}
          </p>
          <p className="text-muted font-mono text-xs">
            {Math.min(index, total)} / {total}
          </p>
        </div>
        <div className="bg-rule mt-3 h-1 w-full overflow-hidden rounded-full">
          <div
            className="bg-accent h-full transition-[width] duration-500"
            style={{ width: `${(Math.min(index - 1, total) / total) * 100}%` }}
          />
        </div>

        {finish.isError && (
          <div className="mt-6">
            <Alert>{getErrorMessage(finish.error)}</Alert>
          </div>
        )}

        {index > total ? (
          <FinishPrompt onFinish={doFinish} />
        ) : (
          <QuestionStep
            key={index}
            sessionId={session.id}
            index={index}
            isLast={index >= total}
            onForward={goForward}
          />
        )}
      </div>
    </Container>
  )
}

function FinishPrompt({ onFinish }: { onFinish: () => void }) {
  return (
    <div className="mt-10 text-center">
      <h1 className="text-ink font-display text-2xl">Все вопросы отвечены</h1>
      <p className="text-muted mx-auto mt-2 max-w-md text-sm">
        Завершите интервью, чтобы получить разбор ответов и итоговую оценку.
      </p>
      <Button size="lg" className="mt-6" onClick={onFinish}>
        Завершить интервью
      </Button>
    </div>
  )
}

function QuestionStep({
  sessionId,
  index,
  isLast,
  onForward,
}: {
  sessionId: string
  index: number
  isLast: boolean
  onForward: () => void
}) {
  const {
    data: question,
    isLoading,
    isError,
    error,
  } = useQuestion(sessionId, index)
  const submit = useSubmitAnswer()
  const [answer, setAnswer] = useState('')
  const [reviewed, setReviewed] = useState<{
    score: number | null
    feedback: string | null
  } | null>(null)

  if (isLoading) {
    return <p className="text-muted mt-10 text-sm">Загрузка вопроса…</p>
  }

  if (isError || !question) {
    return (
      <div className="mt-10">
        <Alert>{getErrorMessage(error)}</Alert>
      </div>
    )
  }

  const send = (evaluate: boolean) => {
    if (!answer.trim()) return
    submit.mutate(
      {
        sessionId,
        questionId: question.questionId,
        answerText: answer,
        evaluate,
      },
      {
        onSuccess: (res) => {
          if (evaluate)
            setReviewed({ score: res.score, feedback: res.feedback })
          else onForward()
        },
      },
    )
  }

  return (
    <div className="mt-8">
      <h1 className="text-ink font-display text-2xl leading-snug">
        {question.questionText}
      </h1>

      {reviewed ? (
        <div className="mt-6 space-y-6">
          <div className="border-rule bg-paper-2/60 rounded-md border p-4">
            <p className="text-muted mb-1 text-xs">Ваш ответ</p>
            <p className="text-ink whitespace-pre-wrap">{answer}</p>
          </div>
          {reviewed.feedback ? (
            <MarginNote score={reviewed.score ?? undefined}>
              {reviewed.feedback}
            </MarginNote>
          ) : (
            <p className="text-muted text-sm">
              Разбор недоступен — оценку добавим в итоговом отчёте.
            </p>
          )}
          <Button size="lg" onClick={onForward}>
            {isLast ? 'Завершить интервью' : 'Следующий вопрос'}
          </Button>
        </div>
      ) : (
        <div className="mt-6">
          <Textarea
            label="Ваш ответ"
            value={answer}
            onChange={(e) => setAnswer(e.target.value)}
            placeholder="Отвечайте так, как отвечали бы на собеседовании…"
            disabled={submit.isPending}
          />

          {submit.isError && (
            <div className="mt-4">
              <Alert>{getErrorMessage(submit.error)}</Alert>
            </div>
          )}

          <div className="mt-5 flex flex-col gap-3 sm:flex-row">
            <Button
              size="lg"
              onClick={() => send(true)}
              disabled={!answer.trim() || submit.isPending}
            >
              {submit.isPending ? 'Оцениваем ответ…' : 'Ответить с разбором'}
            </Button>
            <Button
              variant="secondary"
              size="lg"
              onClick={() => send(false)}
              disabled={!answer.trim() || submit.isPending}
            >
              Ответить без разбора
            </Button>
          </div>
          <p className="text-muted mt-3 text-xs">
            С разбором — рецензент оценит ответ сразу. Без разбора — быстрее,
            весь разбор придёт в итоговом отчёте.
          </p>
        </div>
      )}
    </div>
  )
}
