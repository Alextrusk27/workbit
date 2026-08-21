import { useEffect, useRef, useState } from 'react'
import { ChatShell } from '@/components/chat/ChatShell'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { TypingDots } from '@/components/chat/TypingDots'
import { Stars } from '@/components/ui/Stars'
import { IconMic, IconSend } from '@/components/marketing/icons'
import { motionConfig } from '@/lib/motion'
import { cn } from '@/lib/cn'

const QUESTION = 'Чем отличается HashMap от ConcurrentHashMap?'
const ANSWER =
  'HashMap не потокобезопасен, а ConcurrentHashMap разрешает конкурентный доступ и блокирует не всю таблицу, а сегменты…'
const REVIEW =
  'Верно про сегменты. Уточните: в Java 8+ это блокировка на уровне бакета.'

const TYPE_QUESTION_MS = 28
const TYPE_REVIEW_MS = 18

const PHASES = [
  'ask',
  'question',
  'recording',
  'answer',
  'reviewing',
  'review',
] as const
type Phase = (typeof PHASES)[number]

const reached = (current: Phase, target: Phase) =>
  PHASES.indexOf(current) >= PHASES.indexOf(target)

const WAVE_HEIGHTS = ['100%', '70%', '85%', '55%']
const WAVE_DELAYS = ['0s', '0.15s', '0.3s', '0.45s']

function Wave() {
  return (
    <span
      aria-hidden
      className="mt-[3px] mb-[7px] flex h-4 items-center gap-[3px]"
    >
      {Array.from({ length: 12 }, (_, i) => (
        <span
          key={i}
          className="wave-bar"
          style={{
            height: WAVE_HEIGHTS[i % 4],
            animationDelay: WAVE_DELAYS[i % 4],
          }}
        />
      ))}
    </span>
  )
}

/** Живая витрина продукта в герое: вопрос печатается, кандидат отвечает
 *  голосом, рецензент разбирает ответ. Цикл повторяется — гаснет и снова
 *  наполняется только лента сообщений. При reduced-motion и на слабых
 *  устройствах показывается финальный кадр без анимации.
 *
 *  Сообщения появляются без входной анимации — как в макете, иначе смена
 *  пузыря записи на текст ответа выглядит прыжком. Текст печатается прямо в
 *  DOM через ref: setState на каждый символ давал сорок ререндеров в секунду. */
export function HeroChatDemo() {
  const animated = motionConfig.shouldAnimate({ essential: true })
  const [phase, setPhase] = useState<Phase>(animated ? 'ask' : 'review')
  const [fading, setFading] = useState(false)
  const [visible, setVisible] = useState(true)
  const rootRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const questionRef = useRef<HTMLSpanElement>(null)
  const reviewRef = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    if (!animated) return
    const root = rootRef.current
    if (!root) return
    const observer = new IntersectionObserver(([entry]) =>
      setVisible(entry.isIntersecting),
    )
    observer.observe(root)
    return () => observer.disconnect()
  }, [animated])

  useEffect(() => {
    if (!animated || !visible) return
    let cancelled = false

    const sleep = (ms: number) =>
      new Promise((resolve) => setTimeout(resolve, ms))
    const painted = () =>
      new Promise((resolve) =>
        requestAnimationFrame(() => requestAnimationFrame(resolve)),
      )
    const scrollBottom = () => {
      const body = bodyRef.current
      if (body) body.scrollTop = body.scrollHeight
    }

    const type = async (
      target: React.RefObject<HTMLSpanElement | null>,
      text: string,
      pace: number,
    ) => {
      await painted()
      const el = target.current
      if (!el || cancelled) return
      el.classList.add('caret-type')
      for (let i = 1; i <= text.length; i += 1) {
        if (cancelled) return
        el.textContent = text.slice(0, i)
        scrollBottom()
        await sleep(pace)
      }
      el.classList.remove('caret-type')
    }

    const step = async (next: Phase, wait: number) => {
      if (cancelled) return
      setPhase(next)
      await painted()
      scrollBottom()
      await sleep(wait)
    }

    const run = async () => {
      while (!cancelled) {
        setFading(false)
        setPhase('ask')
        if (questionRef.current) questionRef.current.textContent = ''
        if (reviewRef.current) reviewRef.current.textContent = ''
        await sleep(1100)
        if (cancelled) return

        setPhase('question')
        await type(questionRef, QUESTION, TYPE_QUESTION_MS)
        await sleep(800)

        await step('recording', 3900)
        await step('answer', 700)
        await step('reviewing', 1200)

        if (cancelled) return
        setPhase('review')
        await type(reviewRef, REVIEW, TYPE_REVIEW_MS)
        await sleep(4500)

        if (cancelled) return
        setFading(true)
        await sleep(450)
      }
    }

    void run()
    return () => {
      cancelled = true
    }
  }, [animated, visible])

  const recording = phase === 'recording'

  return (
    <div
      ref={rootRef}
      role="img"
      className="overflow-anchor-none"
      aria-label="Пример интервью: вопрос про HashMap, голосовой ответ кандидата и разбор рецензента в отчёте с оценкой 4 из 5"
    >
      <div aria-hidden>
        <ChatShell
          name="AI-интервьюер"
          status="онлайн"
          bodyRef={bodyRef}
          bodyClassName={cn(
            'h-85 transition-opacity duration-[400ms]',
            fading ? 'opacity-0' : 'opacity-100',
          )}
          footer={
            <>
              <span
                className={cn(
                  'bg-glass grid size-9 shrink-0 place-items-center rounded-full border transition-colors',
                  recording
                    ? 'border-danger/45 text-danger mic-pulse'
                    : 'border-line text-muted',
                )}
              >
                <IconMic className="size-[17px]" />
              </span>
              <span
                className={cn(
                  'bg-surface flex min-h-9.5 flex-1 items-center overflow-hidden rounded-md border px-3 py-2 text-[13.5px] whitespace-nowrap transition-colors',
                  recording
                    ? 'border-danger/40 text-danger'
                    : 'border-line text-dim',
                )}
              >
                {recording
                  ? 'Идёт запись голоса…'
                  : 'Отвечайте текстом или голосом…'}
              </span>
              <span className="bg-grad grid size-9 shrink-0 place-items-center rounded-full text-white shadow-[0_4px_14px_rgba(99,102,241,0.35)]">
                <IconSend className="size-4" />
              </span>
            </>
          }
        >
          <ChatBubble role="bot" who="Вопрос 3 / 10">
            {phase === 'ask' ? (
              <TypingDots />
            ) : (
              <span ref={questionRef}>{animated ? '' : QUESTION}</span>
            )}
          </ChatBubble>

          {recording && (
            <ChatBubble role="user" who="Вы · голосовой ответ">
              <Wave />
            </ChatBubble>
          )}

          {reached(phase, 'answer') && (
            <ChatBubble role="user" who="Вы · голосовой ответ">
              {ANSWER}
            </ChatBubble>
          )}

          {reached(phase, 'reviewing') && (
            <ChatBubble role="bot" who="Разбор в отчёте">
              {phase === 'reviewing' ? (
                <TypingDots />
              ) : (
                <>
                  <span className="mb-1.5 flex items-center gap-2 text-[13px]">
                    <Stars value={4} />
                    <span className="text-dim">4 из 5</span>
                  </span>
                  <span ref={reviewRef}>{animated ? '' : REVIEW}</span>
                </>
              )}
            </ChatBubble>
          )}
        </ChatShell>
      </div>
    </div>
  )
}
