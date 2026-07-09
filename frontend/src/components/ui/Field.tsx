import type { InputHTMLAttributes } from 'react'
import { useId } from 'react'
import { cn } from '@/lib/cn'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Подпись-подсказка под полем (напр. «минимум 8 символов»). */
  hint?: string
}

/** Поле формы с подписью в стиле дизайн-системы. */
export function Field({
  label,
  hint,
  className,
  id,
  type,
  ...props
}: FieldProps) {
  const autoId = useId()
  const inputId = id ?? autoId
  const hintId = hint ? `${inputId}-hint` : undefined
  const emailProps =
    type === 'email'
      ? {
          spellCheck: false,
          autoCapitalize: 'none' as const,
          autoCorrect: 'off',
        }
      : {}
  return (
    <div className={className}>
      <label htmlFor={inputId} className="text-ink block text-sm font-medium">
        {label}
      </label>
      <input
        id={inputId}
        type={type}
        aria-describedby={hintId}
        className={cn(
          'border-rule bg-paper text-ink mt-2 h-11 w-full rounded-md border px-3 text-base',
          'placeholder:text-muted/70 transition-colors',
          'hover:border-ink/30 focus:border-accent focus:outline-none',
          'focus-visible:outline-accent focus-visible:outline-2 focus-visible:outline-offset-2',
        )}
        {...emailProps}
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
