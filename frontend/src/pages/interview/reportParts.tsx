import { Link } from 'react-router-dom'
import { FeedbackWidget } from '@/components/app/FeedbackWidget'
import { Eyebrow } from '@/components/ui/Eyebrow'
import { MarginNote } from '@/components/ui/MarginNote'
import { Stars } from '@/components/ui/Stars'
import { buttonClasses } from '@/components/ui/buttonStyles'
import {
  interviewApi,
  type InterviewQuestion,
  type OfferProbability,
} from '@/features/interview/api'
import { OFFER_TONE, type OfferTone } from '@/features/interview/labels'
import { cn } from '@/lib/cn'
import { answersWord } from '@/lib/plural'

/** Отвеченный вопрос в режиме чтения: текст вопроса и ответ пользователя. */
export function QuestionEntry({
  orderIndex,
  followUp,
  questionText,
  answerText,
}: {
  orderIndex: number
  followUp: boolean
  questionText: string
  answerText: string | null
}) {
  return (
    <div>
      {followUp ? (
        <span className="bg-indigo/12 text-indigo rounded-sm px-2.5 py-[3px] text-xs font-semibold">
          Уточняющий вопрос
        </span>
      ) : (
        <Eyebrow className="tracking-[0.08em]">Вопрос {orderIndex}</Eyebrow>
      )}
      <h3 className="text-ink mt-2 text-lg leading-snug font-bold break-words">
        {questionText}
      </h3>
      <p className="text-muted mt-3 break-words whitespace-pre-wrap">
        {answerText || <span className="text-dim italic">Без ответа</span>}
      </p>
    </div>
  )
}

/** Вопрос в отчёте: ответ кандидата и пометка рецензента с оценкой за весь
 *  кейс (уточнения в отчёт не попадают — их удаляют при завершении). */
export function CaseEntry({
  question,
  sessionId,
}: {
  question: InterviewQuestion
  sessionId: string
}) {
  return (
    <div>
      <QuestionEntry
        orderIndex={question.orderIndex}
        followUp={question.followUp}
        questionText={question.questionText}
        answerText={question.answerText}
      />
      {question.feedback && (
        <MarginNote score={question.score ?? undefined} className="mt-4">
          {question.feedback}
        </MarginNote>
      )}
      <FeedbackWidget
        className="mt-4"
        submit={(body) =>
          interviewApi.questionFeedback(sessionId, question.questionId, body)
        }
      />
    </div>
  )
}

const OFFER_CLASS: Record<OfferTone, string> = {
  low: 'text-muted',
  mid: 'text-grad',
  high: 'text-ok',
}

/** Вероятность оффера — крупная подсветка в тон вердикта. */
export function OfferBadge({ value }: { value: OfferProbability }) {
  const tone = OFFER_TONE[value] ?? 'mid'
  return (
    <span
      className={cn(
        'text-[34px] leading-none font-extrabold tracking-[-0.02em]',
        OFFER_CLASS[tone],
      )}
    >
      {value}
    </span>
  )
}

/** Итог разбора: средний балл, вероятность оффера, текстовый вывод рецензента
 *  и рекомендации к подготовке (если рецензент их дал). */
export function ReportSummary({
  avgScore,
  offerProbability,
  overallFeedback,
  recommendations,
  weakestSkill,
  trainingTo,
  answeredCount,
  sessionId,
}: {
  avgScore: number | null
  offerProbability: OfferProbability
  overallFeedback: string
  recommendations: string | null
  weakestSkill: string | null
  trainingTo: string
  answeredCount: number
  sessionId: string
}) {
  return (
    <div className="grid gap-5 sm:grid-cols-2">
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

      <div className="border-line bg-card rounded-xl border p-6">
        <Eyebrow>Вероятность оффера</Eyebrow>
        <p className="mt-2.5">
          <OfferBadge value={offerProbability} />
        </p>
        <p className="text-dim mt-1.5 text-[13px]">
          По итогам {answeredCount} {answersWord(answeredCount)}
        </p>
      </div>

      <div className="border-line bg-card rounded-xl border p-6 sm:col-span-2">
        <Eyebrow>Итоговый фидбэк</Eyebrow>
        <p className="text-muted mt-3 max-w-[78ch] text-[15px] whitespace-pre-wrap">
          {overallFeedback}
        </p>
        <FeedbackWidget
          className="border-divider mt-[18px] border-t pt-3.5"
          submit={(body) => interviewApi.reportFeedback(sessionId, body)}
        />
      </div>

      {recommendations && (
        <div className="border-line bg-card rounded-xl border p-6 sm:col-span-2">
          <Eyebrow>Рекомендации</Eyebrow>
          <p className="text-muted mt-3 max-w-[78ch] text-[15px] whitespace-pre-wrap">
            {recommendations}
          </p>
        </div>
      )}

      {weakestSkill && (
        <div className="border-line bg-card flex flex-wrap items-center justify-between gap-4 rounded-xl border p-6 sm:col-span-2">
          <div>
            <Eyebrow>Самое слабое место</Eyebrow>
            <p className="text-ink mt-2 text-[21px] leading-snug font-bold tracking-[-0.015em] break-words">
              {weakestSkill}
            </p>
            <p className="text-dim mt-1.5 text-[13px]">
              Тренировка соберёт вопросы только по этому навыку
            </p>
          </div>
          <Link to={trainingTo} className={buttonClasses({ size: 'sm' })}>
            Тренировать навык
          </Link>
        </div>
      )}

      <p className="text-dim text-xs sm:col-span-2">
        Разбор сгенерирован ИИ и может содержать ошибки. Относись к оценкам и
        рекомендациям как к ориентиру.
      </p>
    </div>
  )
}
