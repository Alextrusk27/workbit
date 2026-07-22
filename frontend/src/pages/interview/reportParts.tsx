import { MarginNote } from '@/components/ui/MarginNote'
import { Stars } from '@/components/ui/Stars'
import type {
  InterviewQuestion,
  OfferProbability,
} from '@/features/interview/api'
import { OFFER_TONE, type OfferTone } from '@/features/interview/labels'
import { cn } from '@/lib/cn'

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
      <p className="text-muted font-mono text-xs">Вопрос {orderIndex}</p>
      <h3 className="text-ink font-display mt-1.5 text-lg leading-snug break-words">
        {questionText}
      </h3>
      <div className="border-rule bg-paper-2/60 mt-3 rounded-md border p-4">
        <p className="text-muted mb-1 text-xs">Ваш ответ</p>
        <p className="text-ink break-words whitespace-pre-wrap">
          {answerText || <span className="text-muted italic">Без ответа</span>}
        </p>
      </div>
    </div>
  )
}

/** Вопрос в отчёте: ответ кандидата и пометка рецензента с оценкой. */
export function CaseEntry({ question }: { question: InterviewQuestion }) {
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
    </div>
  )
}

const OFFER_CLASS: Record<OfferTone, string> = {
  low: 'border-rule bg-paper-2 text-muted',
  mid: 'bg-accent/10 text-accent',
  high: 'bg-pine/10 text-pine',
}

/** Вероятность оффера — цветной лейбл (в палитре нет красного, «низкая» нейтральна). */
export function OfferBadge({ value }: { value: OfferProbability }) {
  const tone = OFFER_TONE[value] ?? 'mid'
  return (
    <span
      className={cn(
        'font-display inline-block rounded-md px-3 py-1 text-lg',
        OFFER_CLASS[tone],
      )}
    >
      {value}
    </span>
  )
}

/** Итог разбора: средний балл, вероятность оффера и текстовый вывод рецензента. */
export function ReportSummary({
  avgScore,
  offerProbability,
  overallFeedback,
}: {
  avgScore: number | null
  offerProbability: OfferProbability
  overallFeedback: string
}) {
  return (
    <div>
      <div className="flex flex-wrap gap-4">
        <div className="border-rule bg-paper-2/60 min-w-[13rem] flex-1 rounded-lg border p-5 sm:max-w-xs">
          <p className="text-muted text-xs">Средняя оценка</p>
          {avgScore != null ? (
            <>
              <div className="text-accent mt-2 text-2xl">
                <Stars value={Math.round(avgScore * 2) / 2} />
              </div>
              <p className="text-muted mt-1.5 font-mono text-sm">
                {avgScore.toFixed(1).replace('.', ',')} из 5
              </p>
            </>
          ) : (
            <p className="text-muted mt-2 text-sm">Оценка недоступна</p>
          )}
        </div>

        <div className="border-rule bg-paper-2/60 min-w-[13rem] flex-1 rounded-lg border p-5 sm:max-w-xs">
          <p className="text-muted text-xs">Вероятность оффера</p>
          <div className="mt-2">
            <OfferBadge value={offerProbability} />
          </div>
          <p className="text-muted mt-2 text-xs">
            Оценка рецензента по вашим ответам на вопросы вакансии
          </p>
        </div>
      </div>

      <div className="mt-8">
        <h2 className="text-ink font-display text-xl">Итог рецензента</h2>
        <p className="text-ink mt-3 leading-relaxed whitespace-pre-wrap">
          {overallFeedback}
        </p>
        <p className="text-muted mt-6 text-xs">
          Разбор сгенерирован ИИ и может содержать ошибки. Относитесь к оценкам
          и рекомендациям как к ориентиру.
        </p>
      </div>
    </div>
  )
}
