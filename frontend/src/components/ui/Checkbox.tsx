import type { ReactNode } from 'react'
import { useId } from 'react'

interface CheckboxProps {
  checked: boolean
  onChange: (checked: boolean) => void
  children: ReactNode
  required?: boolean
}

export function Checkbox({
  checked,
  onChange,
  children,
  required,
}: CheckboxProps) {
  const id = useId()
  return (
    <div className="flex items-start gap-2.5">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        required={required}
        onChange={(e) => onChange(e.target.checked)}
        className="accent-indigo mt-0.5 size-4 shrink-0"
      />
      <label htmlFor={id} className="text-muted text-[13px] leading-snug">
        {children}
      </label>
    </div>
  )
}
