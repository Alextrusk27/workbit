import type { ReactNode } from 'react'

/** Финальный призыв: подсвеченная панель с кнопками. */
export function CtaPanel({
  title,
  children,
  actions,
}: {
  title: string
  children: ReactNode
  actions: ReactNode
}) {
  return (
    <div className="border-violet/30 relative overflow-hidden rounded-3xl border bg-[linear-gradient(135deg,rgba(99,102,241,0.16),rgba(139,92,246,0.12)_60%,rgba(103,232,249,0.06))] px-8 py-11 text-center sm:py-18">
      <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">{title}</h2>
      <p className="text-muted mx-auto mt-3 max-w-[46ch] text-[17px]">
        {children}
      </p>
      <div className="mt-7.5 flex flex-wrap justify-center gap-3.5">
        {actions}
      </div>
    </div>
  )
}
