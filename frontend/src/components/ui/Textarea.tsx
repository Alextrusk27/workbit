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
      <label htmlFor={inputId} className="text-ink block text-sm font-medium">
        {label}
      </label>
      <textarea
        id={inputId}
        rows={rows}
        aria-describedby={hintId}
        className={cn(
          'border-rule bg-paper text-ink mt-2 w-full rounded-md border px-3 py-2.5 text-base',
          'placeholder:text-muted/70 resize-y transition-colors',
          'hover:border-ink/30 focus:border-accent focus:outline-none',
          'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
        )}
        {...props}
      />
      {hint && (
        <p id={hintId} className="text-muted mt-1.5 text-xs">
          {hint}
        </p>
      )}
    </div>
  )
}
