import { Eyebrow } from '@/components/ui/Eyebrow'
import { MarginNote } from '@/components/ui/MarginNote'
import { Stars } from '@/components/ui/Stars'
import type { TrainingQuestion } from '@/features/training/api'

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

/** Кейс в отчёте: основной вопрос с ответом и пометка рецензента с оценкой
 *  за весь кейс (уточнения в отчёт не попадают — их удаляют при завершении). */
export function CaseEntry({ question }: { question: TrainingQuestion }) {
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
    </div>
  )
}

/** Итог разбора: средний балл и текстовый вывод рецензента. */
export function ReportSummary({
  avgScore,
  overallFeedback,
}: {
  avgScore: number | null
  overallFeedback: string
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
          Разбор сгенерирован ИИ и может содержать ошибки. Относитесь к оценкам
          и рекомендациям как к ориентиру.
        </p>
      </div>
    </div>
  )
}
