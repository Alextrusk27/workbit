import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { FeedbackWidget } from '@/components/app/FeedbackWidget'
import { Alert } from '@/components/ui/Alert'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { MarginNote } from '@/components/ui/MarginNote'
import { Spinner } from '@/components/ui/Spinner'
import { Stars } from '@/components/ui/Stars'
import { OPERATION_COST } from '@/content/limits'
import { billingKeys, useBalance } from '@/features/billing/useBilling'
import { trainingApi, type TrainingQuestion } from '@/features/training/api'
import { trainingErrorMessage } from '@/features/training/errors'
import { keys, useReferenceAnswer } from '@/features/training/useTraining'

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

function LockIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="13"
      height="13"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0"
    >
      <rect x="4" y="11" width="16" height="10" rx="2" />
      <path d="M8 11V7a4 4 0 0 1 8 0v4" />
    </svg>
  )
}

const REFERENCE_LINK_CLASS =
  'text-dim hover:text-ink focus-visible:outline-indigo mt-4 rounded-sm text-[13px] underline underline-offset-4 transition-colors focus-visible:outline-2 focus-visible:outline-offset-2'

/** Эталонный ответ по кнопке: до первого клика запрос не уходит, дальше
 *  ответ живёт в кэше — у сгенерированного вопроса его пишет LLM.
 *  Первый просмотр стоит 1 лимит и открыт только после покупки, поэтому
 *  кнопка показывает цену, пока `unlocked` не стал true (сам флаг из DTO
 *  или удачный ответ в этом кэше), а без покупки — замок со ссылкой на тарифы.
 *  `withFeedback` добавляет лайк/дизлайк под текстом — для прогона, где
 *  виджета кейса ещё нет; в отчёте оценивается кейс целиком. */
export function ReferenceAnswer({
  sessionId,
  questionId,
  unlocked,
  withFeedback = false,
}: {
  sessionId: string
  questionId: string
  unlocked: boolean
  withFeedback?: boolean
}) {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const { data: balance } = useBalance()
  const { data, isFetching, isError, error } = useReferenceAnswer(
    sessionId,
    questionId,
    open,
  )
  const isUnlocked = unlocked || data !== undefined

  useEffect(() => {
    if (!data || unlocked) return
    qc.invalidateQueries({ queryKey: billingKeys.quota })
    qc.invalidateQueries({ queryKey: keys.report(sessionId) })
  }, [data, unlocked, qc, sessionId])

  if (!open) {
    if (!isUnlocked && balance?.paid === false) {
      return (
        <Link
          to="/pricing"
          title="Доступно после первого пополнения"
          className={`${REFERENCE_LINK_CLASS} inline-flex items-center gap-1.5`}
        >
          <LockIcon />
          Эталонный ответ — после первого пополнения
        </Link>
      )
    }
    return (
      <button
        type="button"
        onClick={() => setOpen(true)}
        className={REFERENCE_LINK_CLASS}
      >
        Посмотреть эталонный ответ
        {!isUnlocked && balance?.paid && ` · ${OPERATION_COST.reference} лимит`}
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
          <Alert>{trainingErrorMessage(error, 'reference')}</Alert>
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
      <ReferenceAnswer
        sessionId={sessionId}
        questionId={question.questionId}
        unlocked={question.referenceAnswerUnlocked}
      />
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
