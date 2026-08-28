import { useState } from 'react'
import { FeedbackWidget } from '@/components/app/FeedbackWidget'
import { Alert } from '@/components/ui/Alert'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { MarginNote } from '@/components/ui/MarginNote'
import { Spinner } from '@/components/ui/Spinner'
import { Stars } from '@/components/ui/Stars'
import { trainingApi, type TrainingQuestion } from '@/features/training/api'
import { useReferenceAnswer } from '@/features/training/useTraining'
import { getErrorMessage } from '@/lib/api'

/** Отвеченный вопрос в режиме чтения: текст вопроса и ответ пользователя. */
export function QuestionEntry({
  orderIndex,
  questionText,
  answerText,
}: {
  orderIndex: number
  questionText: string
  answerText: string | null
}) {
  return (
    <div>
      <Eyebrow className="tracking-[0.08em]">Вопрос {orderIndex}</Eyebrow>
      <h3 className="text-ink mt-2 text-lg leading-snug font-bold break-words">
        {questionText}
      </h3>
      <p className="text-muted mt-3 break-words whitespace-pre-wrap">
        {answerText || <span className="text-dim italic">Без ответа</span>}
      </p>
    </div>
  )
}

/** Эталонный ответ по кнопке: до первого клика запрос не уходит, дальше
 *  ответ живёт в кэше — у сгенерированного вопроса его пишет LLM.
 *  `withFeedback` добавляет лайк/дизлайк под текстом — для прогона, где
 *  виджета кейса ещё нет; в отчёте оценивается кейс целиком. */
export function ReferenceAnswer({
  sessionId,
  questionId,
  withFeedback = false,
}: {
  sessionId: string
  questionId: string
  withFeedback?: boolean
}) {
  const [open, setOpen] = useState(false)
  const { data, isFetching, isError, error } = useReferenceAnswer(
    sessionId,
    questionId,
    open,
  )

  if (!open) {
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-dim hover:text-ink focus-visible:outline-indigo mt-4 rounded-sm text-[13px] underline underline-offset-4 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2"
      >
        Посмотреть эталонный ответ
      </button>
    )
  }

  return (
    <div className="border-line bg-glass mt-4 rounded-xl border p-5">
      <Eyebrow>Эталонный ответ</Eyebrow>
      {isFetching && (
        <p role="status" className="text-muted mt-3 text-sm">
          <Spinner className="mr-2.5" />
          Готовим эталонный ответ…
        </p>
      )}
      {isError && (
        <div className="mt-3">
          <Alert>{getErrorMessage(error)}</Alert>
        </div>
      )}
      {data && (
        <>
          <p className="text-muted mt-3 max-w-[78ch] text-[15px] break-words whitespace-pre-wrap">
            {data.answer}
          </p>
          {withFeedback && (
            <FeedbackWidget
              variant="reference"
              className="border-divider mt-4 border-t pt-3.5"
              submit={(body) =>
                trainingApi.questionFeedback(sessionId, questionId, body)
              }
            />
          )}
        </>
      )}
    </div>
  )
}

/** Кейс в отчёте: вопрос с ответом, пометка рецензента с оценкой и эталон по кнопке. */
export function CaseEntry({
  question,
  sessionId,
}: {
  question: TrainingQuestion
  sessionId: string
}) {
  return (
    <div>
      <QuestionEntry
        orderIndex={question.orderIndex}
        questionText={question.questionText}
        answerText={question.answerText}
      />
      {question.feedback && (
        <MarginNote score={question.score ?? undefined} className="mt-4">
          {question.feedback}
        </MarginNote>
      )}
      <ReferenceAnswer sessionId={sessionId} questionId={question.questionId} />
      <FeedbackWidget
        className="mt-4"
        submit={(body) =>
          trainingApi.questionFeedback(sessionId, question.questionId, body)
        }
      />
    </div>
  )
}

/** Итог разбора: средний балл и текстовый вывод рецензента. */
export function ReportSummary({
  avgScore,
  overallFeedback,
  sessionId,
}: {
  avgScore: number | null
  overallFeedback: string
  sessionId: string
}) {
  return (
    <div className="grid gap-5 sm:grid-cols-3">
      <div className="border-line bg-card rounded-xl border p-6">
        <Eyebrow>Средний балл</Eyebrow>
        {avgScore != null ? (
          <>
            <p className="text-ink mt-2.5 text-[34px] leading-none font-extrabold tracking-[-0.02em] tabular-nums">
              {avgScore.toFixed(1).replace('.', ',')}
              <span className="text-muted ml-1 text-[17px] font-medium">
                / 5
              </span>
            </p>
            <p className="mt-2 text-sm">
              <Stars value={Math.round(avgScore * 2) / 2} />
            </p>
          </>
        ) : (
          <p className="text-muted mt-2.5 text-sm">Оценка недоступна</p>
        )}
      </div>

      <div className="border-line bg-card rounded-xl border p-6 sm:col-span-2">
        <Eyebrow>Итог рецензента</Eyebrow>
        <p className="text-muted mt-3 max-w-[78ch] text-[15px] whitespace-pre-wrap">
          {overallFeedback}
        </p>
        <p className="text-dim mt-4 text-xs">
          Разбор сгенерирован ИИ и может содержать ошибки. Относись к оценкам и
          рекомендациям как к ориентиру.
        </p>
        <FeedbackWidget
          className="border-divider mt-[18px] border-t pt-3.5"
          submit={(body) => trainingApi.reportFeedback(sessionId, body)}
        />
      </div>
    </div>
  )
}
