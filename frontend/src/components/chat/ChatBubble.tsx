import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

interface ChatBubbleProps {
  role: 'bot' | 'user'
  /** Подпись над сообщением: «Вопрос 3 / 10», «Разбор», «Вы». */
  who?: ReactNode
  /** Цитата ответа, к которому задан уточняющий вопрос. */
  quote?: { name: string; text: string }
  children?: ReactNode
  className?: string
}

export function ChatBubble({
  role,
  who,
  quote,
  children,
  className,
}: ChatBubbleProps) {
  const bot = role === 'bot'
  return (
    <div
      className={cn(
        'max-w-[85%] rounded-[14px] px-4 py-3 text-sm leading-[1.55]',
        bot
          ? 'border-surface-line bg-surface text-ink self-start rounded-tl-[4px] border'
          : 'bg-grad self-end rounded-tr-[4px] text-white',
        className,
      )}
    >
      {who && (
        <span className="mb-[5px] block text-[11px] font-semibold tracking-[0.05em] uppercase opacity-65">
          {who}
        </span>
      )}
      {quote && (
        <span className="border-indigo bg-glass mb-2 block rounded-[4px_8px_8px_4px] border-l-2 px-2.5 py-1.5">
          <span className="text-indigo mb-0.5 block text-[11px] font-semibold">
            {quote.name}
          </span>
          <span className="text-muted line-clamp-2 block text-[12.5px] leading-snug">
            {quote.text}
          </span>
        </span>
      )}
      <span className="block whitespace-pre-wrap">{children}</span>
    </div>
  )
}
