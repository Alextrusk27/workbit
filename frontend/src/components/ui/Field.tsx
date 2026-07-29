import type { InputHTMLAttributes } from 'react'
import { useId } from 'react'
import { cn } from '@/lib/cn'

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  hint?: string
}

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
      <label
        htmlFor={inputId}
        className="text-muted block text-[13.5px] font-semibold"
      >
        {label}
      </label>
      <input
        id={inputId}
        type={type}
        aria-describedby={hintId}
        className={cn(
          'border-line bg-surface text-ink mt-[7px] h-12 w-full rounded-md border px-3.5 text-[15px]',
          'placeholder:text-dim transition-colors',
          'focus:border-indigo focus:ring-indigo/18 focus:ring-[3px] focus:outline-none',
          'disabled:cursor-not-allowed disabled:opacity-50',
        )}
        {...emailProps}
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
