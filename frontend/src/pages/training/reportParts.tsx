import { MarginNote } from '@/components/ui/MarginNote'
import { Stars } from '@/components/ui/Stars'

/** Отвеченный вопрос в режиме чтения: текст вопроса, ответ пользователя и —
 *  если разбор уже сформирован — пометка рецензента с оценкой. */
export function QuestionEntry({
  orderIndex,
  followUp,
  questionText,
  answerText,
  score,
  feedback,
}: {
  orderIndex: number
  followUp: boolean
  questionText: string
  answerText: string | null
  score?: number | null
  feedback?: string | null
}) {
  return (
    <div>
      {followUp ? (
        <span className="bg-accent/10 text-accent rounded-sm px-2 py-0.5 font-mono text-xs">
          Уточняющий вопрос
        </span>
      ) : (
        <p className="text-muted font-mono text-xs">Вопрос {orderIndex}</p>
      )}
      <h3 className="text-ink font-display mt-1.5 text-lg leading-snug break-words">
        {questionText}
      </h3>
      <div className="border-rule bg-paper-2/60 mt-3 rounded-md border p-4">
        <p className="text-muted mb-1 text-xs">Ваш ответ</p>
        <p className="text-ink break-words whitespace-pre-wrap">
          {answerText || <span className="text-muted italic">Без ответа</span>}
        </p>
      </div>
      {feedback && (
        <MarginNote score={score ?? undefined} className="mt-4">
          {feedback}
        </MarginNote>
      )}
    </div>
  )
}

/** Итог разбора: средний балл звёздами и текстовый вывод рецензента. */
export function ReportSummary({
  avgScore,
  overallFeedback,
}: {
  avgScore: number | null
  overallFeedback: string
}) {
  return (
    <div>
      <div className="border-rule bg-paper-2/60 rounded-lg border p-5 sm:max-w-xs">
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

      <div className="mt-8">
        <h2 className="text-ink font-display text-xl">Итог рецензента</h2>
        <p className="text-ink mt-3 leading-relaxed whitespace-pre-wrap">
          {overallFeedback}
        </p>
      </div>
    </div>
  )
}
