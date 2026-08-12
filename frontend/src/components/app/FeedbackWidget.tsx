import { useState } from 'react'
import { AnimatePresence, motion } from 'motion/react'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'
import { motionTokens } from '@/lib/motion'
import { useSafeMotion } from '@/lib/useSafeMotion'

export interface FeedbackBody {
  vote: 'UP' | 'DOWN'
  reasons: string[]
  comment?: string
}

const REASONS = [
  'Вопрос не по теме',
  'Оценка занижена',
  'Разбор поверхностный',
  'Эталонный ответ неточный',
  'Другое',
]

type FeedbackState = 'idle' | 'liked' | 'disliked-open' | 'sent'

function ThumbUpIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="15"
      height="15"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M14 9V5a3 3 0 0 0-3-3l-4 9v11h11.28a2 2 0 0 0 2-1.7l1.38-9a2 2 0 0 0-2-2.3z" />
      <path d="M7 22H4a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2h3" />
    </svg>
  )
}

function ThumbDownIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="15"
      height="15"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M10 15v4a3 3 0 0 0 3 3l4-9V2H5.72a2 2 0 0 0-2 1.7l-1.38 9a2 2 0 0 0 2 2.3z" />
      <path d="M17 2h2.67A2.31 2.31 0 0 1 22 4v7a2.31 2.31 0 0 1-2.33 2H17" />
    </svg>
  )
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      width="15"
      height="15"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 6 9 17l-5-5" />
    </svg>
  )
}

function thumbClasses(selected: boolean): string {
  return cn(
    'flex size-8 touch-manipulation items-center justify-center rounded-md border transition',
    'focus-visible:outline-indigo focus-visible:outline-2 focus-visible:outline-offset-2',
    selected
      ? 'border-indigo/45 bg-indigo/14 text-indigo'
      : 'border-line text-muted hover:bg-glass hover:text-ink bg-transparent',
  )
}

/** Лайк/дизлайк у разбора: лайк уходит сразу, дизлайк раскрывает панель с
 *  причинами. Ошибки отправки глотаются — фидбэк не критичный путь. */
export function FeedbackWidget({
  submit,
  className,
}: {
  submit: (body: FeedbackBody) => Promise<unknown>
  className?: string
}) {
  const [state, setState] = useState<FeedbackState>('idle')
  const [reasons, setReasons] = useState<string[]>([])
  const [comment, setComment] = useState('')
  const panelMotion = useSafeMotion(motionTokens.distance.sm)

  const send = (body: FeedbackBody) => {
    submit(body).catch(() => {})
  }

  const onLike = () => {
    if (state === 'liked') {
      setState('idle')
      return
    }
    setState('liked')
    send({ vote: 'UP', reasons: [] })
  }

  const onDislike = () => {
    if (state === 'disliked-open') {
      setState('idle')
      return
    }
    setReasons([])
    setComment('')
    setState('disliked-open')
  }

  const toggleReason = (reason: string) => {
    setReasons((prev) =>
      prev.includes(reason)
        ? prev.filter((r) => r !== reason)
        : [...prev, reason],
    )
  }

  const canSend = reasons.length > 0 || comment.trim().length > 0

  const onSendDislike = () => {
    send({
      vote: 'DOWN',
      reasons,
      comment: comment.trim() || undefined,
    })
    setState('sent')
  }

  if (state === 'sent') {
    return (
      <p
        className={cn('text-ok flex items-center gap-2 text-[13px]', className)}
      >
        <CheckIcon />
        Спасибо! Отзыв уже у команды — он помогает улучшать вопросы и разборы.
      </p>
    )
  }

  return (
    <div className={className}>
      <div className="flex items-center gap-2.5">
        <span className="text-dim text-[13px]">Разбор полезен?</span>
        <button
          type="button"
          aria-label="Разбор полезен"
          aria-pressed={state === 'liked'}
          onClick={onLike}
          className={thumbClasses(state === 'liked')}
        >
          <ThumbUpIcon />
        </button>
        <button
          type="button"
          aria-label="Разбор не полезен"
          aria-pressed={state === 'disliked-open'}
          onClick={onDislike}
          className={thumbClasses(state === 'disliked-open')}
        >
          <ThumbDownIcon />
        </button>
      </div>

      <AnimatePresence>
        {state === 'disliked-open' && (
          <motion.div
            {...panelMotion}
            transition={{
              duration: motionTokens.duration.fast,
              ease: motionTokens.easing.smooth,
            }}
            className="border-line bg-glass mt-3 max-w-[560px] rounded-xl border p-5"
          >
            <h4 className="text-ink text-sm font-semibold">
              Что не так с разбором?
            </h4>
            <div className="mt-3 flex flex-wrap gap-2">
              {REASONS.map((reason) => {
                const selected = reasons.includes(reason)
                return (
                  <button
                    key={reason}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => toggleReason(reason)}
                    className={cn(
                      'touch-manipulation rounded-full border px-3.5 py-1.5 text-[13px] transition',
                      'focus-visible:outline-indigo focus-visible:outline-2 focus-visible:outline-offset-2',
                      selected
                        ? 'border-indigo/50 bg-indigo/12 text-indigo font-medium'
                        : 'border-line text-muted hover:border-glass-line hover:text-ink bg-transparent',
                    )}
                  >
                    {reason}
                  </button>
                )
              })}
            </div>
            <textarea
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Расскажите своими словами (необязательно)…"
              aria-label="Что не так с разбором"
              className={cn(
                'border-line bg-surface text-ink mt-3 min-h-14 w-full rounded-md border px-3.5 py-2.5 text-sm leading-relaxed',
                'placeholder:text-dim resize-y transition-colors',
                'focus:border-indigo focus:ring-indigo/18 focus:ring-[3px] focus:outline-none',
              )}
            />
            <div className="mt-3.5 flex gap-2.5">
              <Button size="sm" disabled={!canSend} onClick={onSendDislike}>
                Отправить
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setState('idle')}
              >
                Отмена
              </Button>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}
