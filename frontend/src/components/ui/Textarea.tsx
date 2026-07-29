import type { TextareaHTMLAttributes } from 'react'
import { useId } from 'react'
import { cn } from '@/lib/cn'

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string
  hint?: string
}

export function Textarea({
  label,
  hint,
  className,
  id,
  rows = 6,
  ...props
}: TextareaProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const hintId = hint ? `${inputId}-hint` : undefined
  return (
    <div className={className}>
      <label
        htmlFor={inputId}
        className="text-muted block text-[13.5px] font-semibold"
      >
        {label}
      </label>
      <textarea
        id={inputId}
        rows={rows}
        aria-describedby={hintId}
        className={cn(
          'border-line bg-surface text-ink mt-[7px] min-h-35 w-full rounded-md border px-3.5 py-3 text-[15px] leading-relaxed',
          'placeholder:text-dim resize-y transition-colors',
          'focus:border-indigo focus:ring-indigo/18 focus:ring-[3px] focus:outline-none',
        )}
        {...props}
      />
      {hint && (
        <p id={hintId} className="text-dim mt-[7px] text-[12.5px]">
          {hint}
        </p>
      )}
    </div>
  )
}
