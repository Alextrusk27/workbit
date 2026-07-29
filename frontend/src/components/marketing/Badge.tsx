import type { ReactNode } from 'react'

/** Пилюля-подводка над заголовком героя. */
export function Badge({ children }: { children: ReactNode }) {
  return (
    <span className="border-violet/30 bg-violet/10 text-violet inline-flex items-center gap-2 rounded-full border px-3.5 py-[7px] text-[13px] font-medium">
      <span
        aria-hidden
        className="bg-cyan size-1.5 rounded-full shadow-[0_0_8px_var(--color-cyan)]"
      />
      {children}
    </span>
  )
}
