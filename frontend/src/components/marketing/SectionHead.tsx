import type { ReactNode } from 'react'

/** Центрированная шапка секции: заголовок и подводка. */
export function SectionHead({
  title,
  children,
}: {
  title: ReactNode
  children?: ReactNode
}) {
  return (
    <div className="mx-auto mb-14 max-w-155 text-center">
      <h2 className="text-ink text-[clamp(28px,3.6vw,40px)]">{title}</h2>
      {children && <p className="text-muted mt-3.5 text-[17px]">{children}</p>}
    </div>
  )
}
