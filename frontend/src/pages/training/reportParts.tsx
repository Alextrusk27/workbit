import { MarginNote } from '@/components/ui/MarginNote'
import { Stars } from '@/components/ui/Stars'
import type { ReportCase } from './reportCases'

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
    </div>
  )
}

/** Кейс в отчёте: основной вопрос, его уточнения и — если разбор уже
 *  сформирован — общая пометка рецензента с оценкой за весь кейс. */
export function CaseEntry({ main, followUps }: ReportCase) {
  return (
    <div>
      <QuestionEntry
        orderIndex={main.orderIndex}
        followUp={main.followUp}
        questionText={main.questionText}
        answerText={main.answerText}
      />
      {followUps.map((q) => (
        <div key={q.questionId} className="mt-6">
          <QuestionEntry
            orderIndex={q.orderIndex}
            followUp={q.followUp}
            questionText={q.questionText}
            answerText={q.answerText}
          />
        </div>
      ))}
      {main.feedback && (
        <MarginNote score={main.score ?? undefined} className="mt-4">
          {main.feedback}
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
