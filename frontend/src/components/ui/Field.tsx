import type { InputHTMLAttributes } from 'react'
import { useId } from 'react'
import { cn } from '@/lib/cn'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Подпись-подсказка под полем (напр. «минимум 8 символов»). */
  hint?: string
}

/** Поле формы с подписью в стиле дизайн-системы. */
export function Field({ label, hint, className, id, ...props }: FieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  return (
    <div className={className}>
      <label htmlFor={inputId} className="text-ink block text-sm font-medium">
        {label}
      </label>
      <input
        id={inputId}
        className={cn(
          'border-rule bg-paper text-ink mt-2 h-11 w-full rounded-md border px-3 text-base',
          'placeholder:text-muted/70 transition-colors',
          'hover:border-ink/30 focus:border-accent focus:outline-none',
          'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
        )}
        {...props}
      />
      {hint && <p className="text-muted mt-1.5 text-xs">{hint}</p>}
    </div>
  )
}
