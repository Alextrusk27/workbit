import { useEffect, useRef, useState } from 'react'
import { ChatShell } from '@/components/chat/ChatShell'
import { ChatBubble } from '@/components/chat/ChatBubble'
import { TypingDots } from '@/components/chat/TypingDots'
import { Stars } from '@/components/ui/Stars'
import { IconMic, IconSend } from '@/components/marketing/icons'
import { motionConfig } from '@/lib/motion'
import { cn } from '@/lib/cn'

interface Scenario {
  role: string
  question: string
  answer: string
  review: string
  score: number
}

/** Сценарии крутятся по кругу — по одному на цикл анимации. */
const SCENARIOS: Scenario[] = [
  {
    role: 'Интернет-маркетолог',
    question: 'Как поймёшь, что рекламная кампания окупается?',
    answer:
      'Считаю ROMI: доход от кампании минус расходы, делённые на расходы. Ещё смотрю CAC и LTV, чтобы видеть окупаемость на дистанции…',
    review:
      'Хорошо, что связал ROMI с LTV. Уточни, как учтёшь отложенные конверсии.',
    score: 4,
  },
  {
    role: 'Бухгалтер',
    question: 'Чем отличается счёт 60 от счёта 62?',
    answer:
      '60 — расчёты с поставщиками и подрядчиками, 62 — с покупателями и заказчиками. По 60 обычно кредиторка, по 62 — дебиторка…',
    review:
      'Верно. Добавь про авансы: выданные и полученные идут на отдельных субсчетах.',
    score: 5,
  },
  {
    role: 'Python-разработчик',
    question: 'Чем list отличается от tuple?',
    answer:
      'List можно менять, tuple — нет. Tuple пишется в круглых скобках, list — в квадратных… Больше отличий, наверное, не назову.',
    review:
      'База верная, но этого мало. Добавь: tuple хешируем и может быть ключом словаря, а ещё компактнее в памяти.',
    score: 3,
  },
]

const PHASES = [
  'switch',
  'question',
  'recording',
  'answer',
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

/** Живая витрина продукта в герое: интервьюер задаёт вопрос, кандидат
 *  отвечает голосом, рецензент разбирает ответ. Цикл повторяется — лента
 *  гаснет, на смене диалога по центру мигают точки, затем лента наполняется
 *  снова. При reduced-motion и на слабых устройствах показывается финальный
 *  кадр без анимации.
 *
 *  Сообщения появляются целиком с анимацией `msg-in`; пузырь записи и текст
 *  ответа — один элемент, содержимое меняется на месте, иначе смена выглядит
 *  прыжком. */
export function HeroChatDemo() {
  const animated = motionConfig.shouldAnimate({ essential: true })
  const [phase, setPhase] = useState<Phase>(animated ? 'switch' : 'review')
  const [scene, setScene] = useState(0)
  const [fading, setFading] = useState(false)
  const [visible, setVisible] = useState(true)
  const rootRef = useRef<HTMLDivElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)

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

    const step = async (next: Phase, wait: number) => {
      if (cancelled) return
      setPhase(next)
      await painted()
      scrollBottom()
      await sleep(wait)
    }

    const run = async () => {
      let i = 0
      while (!cancelled) {
        setFading(false)
        setPhase('switch')
        setScene(i % SCENARIOS.length)
        i += 1
        await sleep(1400)

        await step('question', 1800)
        await step('recording', 3900)
        await step('answer', 900)
        await step('review', 4500)

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
  const current = SCENARIOS[scene]

  return (
    <div
      ref={rootRef}
      role="img"
      className="overflow-anchor-none"
      aria-label="Пример интервью: вопросы для интернет-маркетолога, бухгалтера и Python-разработчика, голосовой ответ кандидата и разбор рецензента в отчёте с оценкой"
    >
      <div aria-hidden>
        <ChatShell
          name="AI-интервьюер"
          status="онлайн"
          className="grid h-[29rem] grid-cols-[minmax(0,1fr)] grid-rows-[auto_minmax(0,1fr)_auto]"
          bodyRef={bodyRef}
          bodyClassName={cn(
            'min-h-0 transition-opacity duration-[400ms]',
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
                  'bg-surface block min-h-9.5 min-w-0 flex-1 content-center truncate rounded-md border px-3 py-2 text-[13.5px] transition-colors',
                  recording
                    ? 'border-danger/40 text-danger'
                    : 'border-line text-dim',
                )}
              >
                {recording
                  ? 'Идёт запись голоса…'
                  : 'Отвечай текстом или голосом…'}
              </span>
              <span className="bg-grad grid size-9 shrink-0 place-items-center rounded-full text-white shadow-[0_4px_14px_rgba(99,102,241,0.35)]">
                <IconSend className="size-4" />
              </span>
            </>
          }
        >
          {phase === 'switch' && (
            <div className="text-dim my-auto flex justify-center">
              <TypingDots />
            </div>
          )}

          {reached(phase, 'question') && (
            <ChatBubble
              role="bot"
              who={`Вопрос 3 / 10 · ${current.role}`}
              className="msg-in"
            >
              {current.question}
            </ChatBubble>
          )}

          {reached(phase, 'recording') && (
            <ChatBubble
              role="user"
              who="Ты · голосовой ответ"
              className="msg-in"
            >
              {recording ? <Wave /> : current.answer}
            </ChatBubble>
          )}

          {reached(phase, 'review') && (
            <ChatBubble role="bot" who="Разбор в отчёте" className="msg-in">
              <span className="mb-1.5 flex items-center gap-2 text-[13px]">
                <Stars value={current.score} />
                <span className="text-dim">{current.score} из 5</span>
              </span>
              {current.review}
            </ChatBubble>
          )}
        </ChatShell>
      </div>
    </div>
  )
}
