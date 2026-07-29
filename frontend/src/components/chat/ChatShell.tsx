import type { ReactNode, RefObject } from 'react'
import { cn } from '@/lib/cn'

interface ChatShellProps {
  name: string
  status?: string
  children: ReactNode
  footer?: ReactNode
  className?: string
  bodyClassName?: string
  bodyRef?: RefObject<HTMLDivElement | null>
}

/** Рамка чата: шапка с аватаром собеседника, прокручиваемое тело и композер. */
export function ChatShell({
  name,
  status,
  children,
  footer,
  className,
  bodyClassName,
  bodyRef,
}: ChatShellProps) {
  return (
    <div
      className={cn(
        'border-line bg-card shadow-chat overflow-hidden rounded-2xl border',
        className,
      )}
    >
      <div className="border-divider bg-glass flex items-center gap-3 border-b px-4.5 py-3.5">
        <span
          aria-hidden
          className="bg-grad grid size-9 place-items-center rounded-md text-[15px] font-bold text-white"
        >
          w
        </span>
        <div>
          <p className="text-ink text-[14.5px] font-semibold">{name}</p>
          {status && (
            <p className="text-ok flex items-center gap-1.5 text-xs">
              <span aria-hidden className="bg-ok size-1.5 rounded-full" />
              {status}
            </p>
          )}
        </div>
      </div>

      <div
        ref={bodyRef}
        className={cn(
          'scrollbar-slim flex flex-col gap-3.5 overflow-y-auto px-4.5 pt-5 pb-6 [&>*:first-child]:mt-auto',
          bodyClassName,
        )}
      >
        {children}
      </div>

      {footer && (
        <div className="border-divider bg-glass flex items-center gap-2.5 border-t px-3.5 py-3">
          {footer}
        </div>
      )}
    </div>
  )
}
