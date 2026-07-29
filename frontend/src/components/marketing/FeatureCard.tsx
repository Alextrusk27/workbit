import type { ReactNode } from 'react'

/** Карточка фичи или шага: иконка либо номер, заголовок, описание. */
export function FeatureCard({
  icon,
  title,
  children,
}: {
  icon: ReactNode
  title: string
  children: ReactNode
}) {
  return (
    <div className="border-line bg-card hover:border-line-hover h-full rounded-xl border px-6.5 py-7 transition hover:-translate-y-[3px]">
      <div className="border-indigo/25 bg-indigo/12 text-indigo mb-4.5 grid size-11 place-items-center rounded-lg border text-[17px] font-bold">
        {icon}
      </div>
      <h3 className="text-ink text-[17px] font-semibold tracking-[-0.01em]">
        {title}
      </h3>
      <p className="text-muted mt-2 text-[14.5px]">{children}</p>
    </div>
  )
}
